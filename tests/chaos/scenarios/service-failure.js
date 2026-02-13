import http from "k6/http";
import { check, sleep } from "k6";
import { Rate, Trend, Counter } from "k6/metrics";

// ---------------------------------------------------------------------------
// service-failure.js
//
// Chaos scenario: individual backend services are unavailable.
//
// Purpose: verify the gateway returns graceful degradation responses (circuit-
// breaker 503, structured JSON errors) instead of raw 500 Internal Server
// Error pages when a downstream service pod is killed or unreachable.
//
// The script exercises two service categories:
//   1. Catalog-service (read path)  -- GET /api/v1/events, GET /api/v1/artists
//   2. Ticket-service  (write path) -- POST /api/v1/seats/reserve,
//                                      GET  /api/v1/reservations
//
// Intended to run while a Chaos Mesh PodChaos experiment is active that kills
// one or more of those service pods.
// ---------------------------------------------------------------------------

const BASE_URL = __ENV.BASE_URL || "http://localhost:30080";

// -- Custom metrics --------------------------------------------------------

const gracefulDegradation = new Rate("graceful_degradation");
const raw500s = new Counter("raw_500_errors");
const catalogLatency = new Trend("catalog_response_time", true);
const bookingLatency = new Trend("booking_response_time", true);

// -- k6 options ------------------------------------------------------------

export const options = {
  scenarios: {
    service_failure: {
      executor: "constant-vus",
      vus: 50,
      duration: "3m",
    },
  },
  thresholds: {
    // At least 90% of responses must be graceful (structured error, not 500)
    graceful_degradation: ["rate>0.90"],
    // No more than 5% of iterations should produce a raw 500
    raw_500_errors: ["count<150"], // 50 VUs * 3 min ~ 3000 iters; 5% = 150
    // Even under failure, gateway should respond within 10s (timeout ceiling)
    http_req_duration: ["p(95)<10000"],
    catalog_response_time: ["p(95)<8000"],
    booking_response_time: ["p(95)<8000"],
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
 * Determine whether a response represents graceful degradation.
 *
 * Acceptable statuses during a backend outage:
 *   - 200/201       service happened to be up for this request
 *   - 401/403       auth rejection (service is reachable, just unauthorized)
 *   - 404           resource not found (service is reachable)
 *   - 429           rate-limited (gateway-level, Redis-based)
 *   - 502           bad gateway -- acceptable if body is structured JSON
 *   - 503           circuit-breaker / service unavailable -- expected
 *   - 504           gateway timeout -- acceptable, means timeout was enforced
 *
 * NOT acceptable:
 *   - 500 with an HTML body or no structured error payload
 */
function isGraceful(res) {
  const status = res.status;

  // Clearly graceful statuses
  if ([200, 201, 401, 403, 404, 429, 503, 504].includes(status)) {
    return true;
  }

  // 502 is acceptable only if the body looks like structured JSON
  if (status === 502) {
    try {
      JSON.parse(res.body);
      return true;
    } catch (_e) {
      return false;
    }
  }

  // 500 -- only acceptable if it carries a structured JSON error body
  if (status === 500) {
    try {
      const body = JSON.parse(res.body);
      return typeof body.error === "string" || typeof body.message === "string";
    } catch (_e) {
      return false;
    }
  }

  // Anything else (unexpected codes) -- treat as not graceful
  return false;
}

function recordMetrics(res, latencyTrend) {
  const graceful = isGraceful(res);
  gracefulDegradation.add(graceful);

  if (res.status === 500 && !graceful) {
    raw500s.add(1);
  }

  latencyTrend.add(res.timings.duration);
}

// -- Scenario functions ----------------------------------------------------

/**
 * Browse flow: public read endpoints that should degrade gracefully
 * when catalog-service is down.
 */
function browseCatalog() {
  // List events
  const eventsRes = http.get(`${BASE_URL}/api/v1/events`, JSON_HEADERS);
  recordMetrics(eventsRes, catalogLatency);

  check(eventsRes, {
    "events: not a raw 500": (r) => isGraceful(r),
    "events: response has body": (r) => r.body && r.body.length > 0,
    "events: responds within 10s": (r) => r.timings.duration < 10000,
  });

  sleep(0.5);

  // List artists
  const artistsRes = http.get(`${BASE_URL}/api/v1/artists`, JSON_HEADERS);
  recordMetrics(artistsRes, catalogLatency);

  check(artistsRes, {
    "artists: not a raw 500": (r) => isGraceful(r),
    "artists: responds within 10s": (r) => r.timings.duration < 10000,
  });

  sleep(0.5);

  // Single event detail (non-existent ID is fine -- we care about the shape
  // of the error, not the 404 itself)
  const detailRes = http.get(
    `${BASE_URL}/api/v1/events/00000000-0000-0000-0000-000000000001`,
    JSON_HEADERS,
  );
  recordMetrics(detailRes, catalogLatency);

  check(detailRes, {
    "event detail: not a raw 500": (r) => isGraceful(r),
  });
}

/**
 * Booking flow: write endpoints that should degrade gracefully when
 * ticket-service is down.
 */
function attemptBooking() {
  // Attempt a seat reservation (will likely fail auth or 503, both OK)
  const reservePayload = JSON.stringify({
    eventId: "00000000-0000-0000-0000-000000000001",
    seatIds: ["A1", "A2"],
  });

  const reserveRes = http.post(
    `${BASE_URL}/api/v1/seats/reserve`,
    reservePayload,
    JSON_HEADERS,
  );
  recordMetrics(reserveRes, bookingLatency);

  check(reserveRes, {
    "reserve: not a raw 500": (r) => isGraceful(r),
    "reserve: responds within 10s": (r) => r.timings.duration < 10000,
  });

  sleep(0.5);

  // Check reservations list
  const listRes = http.get(
    `${BASE_URL}/api/v1/reservations`,
    JSON_HEADERS,
  );
  recordMetrics(listRes, bookingLatency);

  check(listRes, {
    "reservations list: not a raw 500": (r) => isGraceful(r),
  });
}

// -- Default function (entry point for each VU) ----------------------------

export default function () {
  // Alternate between browse and booking flows
  if (__ITER % 2 === 0) {
    browseCatalog();
  } else {
    attemptBooking();
  }

  sleep(Math.random() * 1.5 + 0.5); // 0.5-2s between iterations
}
