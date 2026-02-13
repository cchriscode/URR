import http from "k6/http";
import { check, sleep } from "k6";
import { Rate, Counter, Trend } from "k6/metrics";

// ---------------------------------------------------------------------------
// redis-failure.js
//
// Chaos scenario: Redis is completely unavailable.
//
// Purpose: verify two critical behaviors when Redis is down:
//
//   1. Rate limiting fails open -- the gateway's RateLimitFilter catches
//      Redis errors and allows requests through (see the fail-open catch
//      block in RateLimitFilter.java).  Under Redis failure, clients must
//      NOT receive 429 responses caused by the rate limiter itself.
//
//   2. Queue service degrades gracefully -- the queue-service depends on
//      Redis for virtual waiting room state.  When Redis is down the service
//      should return a structured error (503) rather than crashing.
//
// This script uses 200 VUs sending rapid-fire requests for 2 minutes to
// stress-test the fail-open path under heavy concurrency.
//
// Intended to run while a Chaos Mesh PodChaos experiment kills the Redis
// pod, or a NetworkChaos experiment blocks traffic to port 6379.
// ---------------------------------------------------------------------------

const BASE_URL = __ENV.BASE_URL || "http://localhost:30080";

// -- Custom metrics --------------------------------------------------------

const rateLimitFailOpen = new Rate("rate_limit_fail_open");
const spurious429s = new Counter("spurious_429_responses");
const queueGraceful = new Rate("queue_graceful_degradation");
const gatewayLatency = new Trend("gateway_response_time", true);

// -- k6 options ------------------------------------------------------------

export const options = {
  scenarios: {
    redis_failure: {
      executor: "constant-vus",
      vus: 200,
      duration: "2m",
    },
  },
  thresholds: {
    // Rate limiter must fail open: at least 95% of general requests should
    // NOT be 429.  A small percentage of 429s is tolerable if they come from
    // residual Redis state before the failure takes full effect.
    rate_limit_fail_open: ["rate>0.95"],

    // Under a 2-minute run with 200 VUs and minimal sleep, we expect
    // tens of thousands of requests.  Fewer than 100 spurious 429s is
    // acceptable (in-flight requests that hit Redis just before it died).
    spurious_429_responses: ["count<100"],

    // Queue service should degrade gracefully (503 with JSON, not crash)
    queue_graceful_degradation: ["rate>0.90"],

    // Gateway itself should still respond quickly even if downstream is broken
    gateway_response_time: ["p(95)<5000"],
    http_req_duration: ["p(95)<5000", "p(99)<8000"],
  },
};

// -- Helpers ---------------------------------------------------------------

const JSON_HEADERS = {
  headers: {
    "Content-Type": "application/json",
    Accept: "application/json",
  },
};

/**
 * Check that a response was NOT a spurious 429 caused by a broken rate
 * limiter.  When Redis is down the filter should fail-open, meaning it
 * should let the request through rather than rejecting it.
 *
 * Returns true if the response is anything other than 429 (fail-open
 * worked) or if the 429 is clearly a legitimate rate limit from a still-
 * running Redis instance.
 */
function didFailOpen(res) {
  return res.status !== 429;
}

function isQueueGraceful(res) {
  // Acceptable: service responded with a structured status
  if ([200, 401, 403, 404, 503].includes(res.status)) {
    return true;
  }

  // 500 only if structured
  if (res.status === 500) {
    try {
      const body = JSON.parse(res.body);
      return typeof body.error === "string" || typeof body.message === "string";
    } catch (_e) {
      return false;
    }
  }

  // 502/504 from gateway are acceptable -- they mean the gateway noticed the
  // downstream failure and reported it structurally
  if ([502, 504].includes(res.status)) {
    return true;
  }

  return false;
}

// -- Scenario flows --------------------------------------------------------

/**
 * Rapid-fire general requests to test that the rate limiter fails open.
 * We deliberately avoid sleeping between requests to maximize the request
 * rate and expose any race conditions in the fail-open path.
 */
function rateLimitStressTest() {
  // Use several endpoint categories to cover all rate-limit buckets

  // GENERAL bucket -- /api/v1/events
  const eventsRes = http.get(`${BASE_URL}/api/v1/events`, JSON_HEADERS);
  gatewayLatency.add(eventsRes.timings.duration);
  const failedOpen = didFailOpen(eventsRes);
  rateLimitFailOpen.add(failedOpen);
  if (!failedOpen) {
    spurious429s.add(1);
  }

  check(eventsRes, {
    "events: rate limiter failed open (not 429)": (r) => didFailOpen(r),
    "events: gateway responded": (r) => r.status !== 0,
    "events: response time under 5s": (r) => r.timings.duration < 5000,
  });

  // AUTH bucket -- /api/v1/auth/me (unauthenticated, should get 401, not 429)
  const authRes = http.get(`${BASE_URL}/api/v1/auth/me`, JSON_HEADERS);
  gatewayLatency.add(authRes.timings.duration);
  const authFailedOpen = didFailOpen(authRes);
  rateLimitFailOpen.add(authFailedOpen);
  if (!authFailedOpen) {
    spurious429s.add(1);
  }

  check(authRes, {
    "auth: rate limiter failed open": (r) => didFailOpen(r),
  });

  // BOOKING bucket -- /api/v1/seats/reserve
  const reservePayload = JSON.stringify({
    eventId: "00000000-0000-0000-0000-000000000001",
    seatIds: ["C1"],
  });
  const bookingRes = http.post(
    `${BASE_URL}/api/v1/seats/reserve`,
    reservePayload,
    JSON_HEADERS,
  );
  gatewayLatency.add(bookingRes.timings.duration);
  const bookingFailedOpen = didFailOpen(bookingRes);
  rateLimitFailOpen.add(bookingFailedOpen);
  if (!bookingFailedOpen) {
    spurious429s.add(1);
  }

  check(bookingRes, {
    "booking: rate limiter failed open": (r) => didFailOpen(r),
  });
}

/**
 * Queue service requests when Redis is down.
 * The queue-service relies on Redis for waiting room state.  Without it,
 * the service should return 503 with a JSON body, not crash.
 */
function queueServiceDegradation() {
  // Join queue attempt
  const joinRes = http.post(
    `${BASE_URL}/api/v1/queue/join`,
    JSON.stringify({
      eventId: "00000000-0000-0000-0000-000000000001",
    }),
    JSON_HEADERS,
  );

  gatewayLatency.add(joinRes.timings.duration);
  queueGraceful.add(isQueueGraceful(joinRes));

  check(joinRes, {
    "queue join: graceful degradation": (r) => isQueueGraceful(r),
    "queue join: has response body": (r) => r.body && r.body.length > 0,
    "queue join: response time under 5s": (r) => r.timings.duration < 5000,
  });

  // Queue position check
  const positionRes = http.get(
    `${BASE_URL}/api/v1/queue/position?eventId=00000000-0000-0000-0000-000000000001`,
    JSON_HEADERS,
  );

  gatewayLatency.add(positionRes.timings.duration);
  queueGraceful.add(isQueueGraceful(positionRes));

  check(positionRes, {
    "queue position: graceful degradation": (r) => isQueueGraceful(r),
    "queue position: response time under 5s": (r) => r.timings.duration < 5000,
  });

  // Queue status
  const statusRes = http.get(
    `${BASE_URL}/api/v1/queue/status`,
    JSON_HEADERS,
  );

  gatewayLatency.add(statusRes.timings.duration);
  queueGraceful.add(isQueueGraceful(statusRes));

  check(statusRes, {
    "queue status: graceful degradation": (r) => isQueueGraceful(r),
  });
}

// -- Default function ------------------------------------------------------

export default function () {
  // 60% rate-limit stress, 40% queue degradation testing
  if (Math.random() < 0.60) {
    rateLimitStressTest();
  } else {
    queueServiceDegradation();
  }

  // Minimal sleep -- we want to hammer the gateway to expose fail-open races
  sleep(Math.random() * 0.3);
}
