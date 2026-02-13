import http from "k6/http";
import { check, sleep } from "k6";
import { Rate, Trend } from "k6/metrics";

// ---------------------------------------------------------------------------
// network-latency.js
//
// Chaos scenario: high network latency between the gateway and backend
// services.
//
// Purpose: verify that the platform enforces request timeouts correctly and
// returns structured timeout responses (504) rather than hanging indefinitely
// or returning unstructured errors.
//
// The script sends requests with varying think-times to simulate slow clients
// and validates that server-side timeouts are respected regardless of client
// behavior.
//
// Intended to run alongside a Chaos Mesh NetworkChaos experiment that injects
// 2-5s of latency on backend service pods.
// ---------------------------------------------------------------------------

const BASE_URL = __ENV.BASE_URL || "http://localhost:30080";

// -- Custom metrics --------------------------------------------------------

const timeoutHandled = new Rate("timeout_handled_gracefully");
const browseLatency = new Trend("browse_latency", true);
const bookingLatency = new Trend("booking_latency", true);
const queueLatency = new Trend("queue_latency", true);

// -- k6 options ------------------------------------------------------------

export const options = {
  scenarios: {
    network_latency: {
      executor: "ramping-vus",
      startVUs: 10,
      stages: [
        { duration: "30s", target: 50 },  // ramp up
        { duration: "3m", target: 100 },  // sustained load
        { duration: "30s", target: 100 }, // hold at peak
        { duration: "1m", target: 0 },    // ramp down
      ],
    },
  },
  thresholds: {
    // Timeouts must be handled gracefully at least 95% of the time
    timeout_handled_gracefully: ["rate>0.95"],
    // Gateway must enforce its own timeout ceiling; p99 should stay under 30s
    // even if backend is artificially delayed
    http_req_duration: ["p(95)<15000", "p(99)<30000"],
    browse_latency: ["p(95)<12000"],
    booking_latency: ["p(95)<15000"],
    queue_latency: ["p(95)<12000"],
    // Failure rate across all checks
    checks: ["rate>0.90"],
  },
};

// -- Helpers ---------------------------------------------------------------

const JSON_HEADERS = {
  headers: {
    "Content-Type": "application/json",
    Accept: "application/json",
  },
};

// Per-request timeout -- k6 will abort the request if the server does not
// respond within this window.  This is intentionally higher than the expected
// gateway timeout so that we observe the gateway's behavior, not k6's.
const REQUEST_PARAMS = Object.assign({}, JSON_HEADERS, { timeout: "25s" });

/**
 * A response is "timeout-handled" if the server responds within its own
 * timeout budget OR returns a structured timeout/unavailable response.
 */
function isTimeoutHandled(res) {
  // k6 reports status 0 when the request itself times out at the client
  if (res.status === 0) {
    return false;
  }

  // Structured error codes from the gateway under latency
  if ([200, 201, 401, 403, 404, 429, 502, 503, 504].includes(res.status)) {
    return true;
  }

  // 500 is acceptable only with a structured JSON body
  if (res.status === 500) {
    try {
      const body = JSON.parse(res.body);
      return typeof body.error === "string" || typeof body.message === "string";
    } catch (_e) {
      return false;
    }
  }

  return false;
}

// -- Scenario flows --------------------------------------------------------

function browseWithSlowNetwork() {
  // Simulate a slow client: random think-time before sending the request
  sleep(Math.random() * 2);

  const res = http.get(`${BASE_URL}/api/v1/events`, REQUEST_PARAMS);

  browseLatency.add(res.timings.duration);
  timeoutHandled.add(isTimeoutHandled(res));

  check(res, {
    "browse: received response (not client timeout)": (r) => r.status !== 0,
    "browse: timeout handled gracefully": (r) => isTimeoutHandled(r),
    "browse: duration under 15s": (r) => r.timings.duration < 15000,
  });

  // Follow up with a detail request if events returned successfully
  if (res.status === 200) {
    try {
      const events = JSON.parse(res.body);
      if (Array.isArray(events) && events.length > 0) {
        const eventId = events[0].id || events[0].eventId;
        if (eventId) {
          sleep(Math.random() * 1.5);
          const detailRes = http.get(
            `${BASE_URL}/api/v1/events/${eventId}`,
            REQUEST_PARAMS,
          );
          browseLatency.add(detailRes.timings.duration);
          timeoutHandled.add(isTimeoutHandled(detailRes));
        }
      }
    } catch (_e) {
      // Response was not JSON; that is tracked by the graceful check
    }
  }
}

function bookingWithSlowNetwork() {
  // Longer think-time for booking -- simulates form fill on slow connection
  sleep(Math.random() * 3 + 1);

  const payload = JSON.stringify({
    eventId: "00000000-0000-0000-0000-000000000001",
    seatIds: ["B1"],
  });

  const res = http.post(
    `${BASE_URL}/api/v1/seats/reserve`,
    payload,
    REQUEST_PARAMS,
  );

  bookingLatency.add(res.timings.duration);
  timeoutHandled.add(isTimeoutHandled(res));

  check(res, {
    "booking: received response": (r) => r.status !== 0,
    "booking: timeout handled gracefully": (r) => isTimeoutHandled(r),
    "booking: duration under 20s": (r) => r.timings.duration < 20000,
  });
}

function queueWithSlowNetwork() {
  sleep(Math.random() * 1.5);

  const res = http.get(
    `${BASE_URL}/api/v1/queue/status`,
    REQUEST_PARAMS,
  );

  queueLatency.add(res.timings.duration);
  timeoutHandled.add(isTimeoutHandled(res));

  check(res, {
    "queue: received response": (r) => r.status !== 0,
    "queue: timeout handled gracefully": (r) => isTimeoutHandled(r),
    "queue: duration under 12s": (r) => r.timings.duration < 12000,
  });
}

// -- Default function ------------------------------------------------------

export default function () {
  const roll = Math.random();

  if (roll < 0.50) {
    browseWithSlowNetwork();
  } else if (roll < 0.80) {
    queueWithSlowNetwork();
  } else {
    bookingWithSlowNetwork();
  }

  // Variable inter-iteration sleep to simulate heterogeneous client speeds
  sleep(Math.random() * 2 + 0.5);
}
