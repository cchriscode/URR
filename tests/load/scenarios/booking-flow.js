import http from 'k6/http';
import { check, sleep, fail } from 'k6';
import { BASE_URL, DEFAULT_HEADERS, THRESHOLDS } from '../lib/config.js';

export const options = {
  stages: [
    { duration: '30s', target: 20 },
    { duration: '3m', target: 100 },
    { duration: '2m', target: 100 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    ...THRESHOLDS,
    'http_req_duration{name:reserve}': ['p(95)<5000'],
    'http_req_duration{name:payment}': ['p(95)<5000'],
  },
};

export function setup() {
  // Register a pool of test users
  const users = [];
  for (let i = 0; i < 50; i++) {
    const email = `booking_${i}_${Date.now()}@test.com`;
    const res = http.post(
      `${BASE_URL}/api/v1/auth/register`,
      JSON.stringify({ email, password: 'Test1234!', name: `Booker ${i}` }),
      { headers: DEFAULT_HEADERS }
    );
    if (res.status === 200 || res.status === 201) {
      users.push({ email, password: 'Test1234!' });
    }
  }

  // Get available events
  const eventsRes = http.get(`${BASE_URL}/api/v1/events`);
  let events = [];
  try {
    events = JSON.parse(eventsRes.body).events || [];
  } catch {
    // empty
  }

  return { users, events };
}

export default function (data) {
  if (!data.users.length || !data.events.length) {
    sleep(1);
    return;
  }

  const user = data.users[__VU % data.users.length];
  const event = data.events[Math.floor(Math.random() * data.events.length)];

  // 1. Login
  const loginRes = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ email: user.email, password: user.password }),
    { headers: DEFAULT_HEADERS }
  );
  check(loginRes, { 'login ok': (r) => r.status === 200 });
  if (loginRes.status !== 200) {
    sleep(2);
    return;
  }
  sleep(0.5);

  // 2. Browse event
  const detailRes = http.get(`${BASE_URL}/api/v1/events/${event.id}`);
  check(detailRes, { 'detail ok': (r) => r.status === 200 });
  sleep(1);

  // 3. Join queue
  const queueRes = http.post(
    `${BASE_URL}/api/v1/queue/check/${event.id}`,
    null,
    { headers: DEFAULT_HEADERS }
  );
  check(queueRes, { 'queue check ok': (r) => r.status === 200 || r.status === 202 });
  sleep(1);

  // 4. Poll queue status (simulate waiting)
  for (let i = 0; i < 3; i++) {
    const statusRes = http.get(`${BASE_URL}/api/v1/queue/status/${event.id}`);
    check(statusRes, { 'queue status ok': (r) => r.status === 200 });
    sleep(2);
  }

  // 5. View seats
  const seatsRes = http.get(`${BASE_URL}/api/v1/seats/events/${event.id}`);
  check(seatsRes, { 'seats ok': (r) => r.status === 200 || r.status === 404 });
  sleep(0.5);

  // 6. Attempt seat reservation (may fail if no seats available)
  let seatIds = [];
  try {
    const seats = JSON.parse(seatsRes.body);
    const available = (seats.seats || seats.data || []).filter(
      (s) => s.status === 'available'
    );
    if (available.length > 0) {
      seatIds = [available[0].id];
    }
  } catch {
    // no seats
  }

  if (seatIds.length > 0) {
    const reserveRes = http.post(
      `${BASE_URL}/api/v1/seats/reserve`,
      JSON.stringify({ eventId: event.id, seatIds }),
      { headers: DEFAULT_HEADERS, tags: { name: 'reserve' } }
    );
    check(reserveRes, {
      'reserve ok': (r) => r.status === 200 || r.status === 201 || r.status === 409,
    });
    sleep(1);

    // 7. Prepare payment (if reservation succeeded)
    if (reserveRes.status === 200 || reserveRes.status === 201) {
      let reservationId;
      try {
        const body = JSON.parse(reserveRes.body);
        reservationId = body.reservationId || body.id;
      } catch {
        // skip
      }

      if (reservationId) {
        const prepareRes = http.post(
          `${BASE_URL}/api/v1/payments/prepare`,
          JSON.stringify({
            reservationId,
            paymentMethod: 'card',
            paymentType: 'reservation',
          }),
          { headers: DEFAULT_HEADERS, tags: { name: 'payment' } }
        );
        check(prepareRes, {
          'payment prepare ok': (r) => r.status === 200 || r.status === 201,
        });
      }
    }
  }

  sleep(2);
}
