import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { BASE_URL, DEFAULT_HEADERS, THRESHOLDS } from '../lib/config.js';

export const options = {
  scenarios: {
    browsers: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '1m', target: 500 },
        { duration: '5m', target: 500 },
        { duration: '1m', target: 0 },
      ],
      exec: 'browseFlow',
    },
    bookers: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '1m', target: 150 },
        { duration: '5m', target: 150 },
        { duration: '1m', target: 0 },
      ],
      exec: 'bookingFlow',
    },
    admins: {
      executor: 'constant-vus',
      vus: 10,
      duration: '7m',
      exec: 'adminFlow',
    },
  },
  thresholds: THRESHOLDS,
};

export function setup() {
  // Get events
  const res = http.get(`${BASE_URL}/api/v1/events`);
  let events = [];
  try {
    events = JSON.parse(res.body).events || [];
  } catch {
    // empty
  }
  return { events };
}

// 70% of traffic: browse only
export function browseFlow(data) {
  group('browse', () => {
    // List events
    const listRes = http.get(`${BASE_URL}/api/v1/events`);
    check(listRes, { 'list ok': (r) => r.status === 200 });
    sleep(2);

    // View random event detail
    if (data.events.length > 0) {
      const event = data.events[Math.floor(Math.random() * data.events.length)];
      http.get(`${BASE_URL}/api/v1/events/${event.id}`);
      sleep(1);

      // Sometimes view artists
      if (Math.random() < 0.3) {
        http.get(`${BASE_URL}/api/v1/artists`);
        sleep(1);
      }

      // Sometimes view seats
      if (Math.random() < 0.5) {
        http.get(`${BASE_URL}/api/v1/seats/events/${event.id}`);
        sleep(1);
      }
    }

    // View news
    if (Math.random() < 0.2) {
      http.get(`${BASE_URL}/api/v1/news`);
      sleep(1);
    }
  });

  sleep(Math.random() * 3 + 2);
}

// 20% of traffic: browse + attempt booking
export function bookingFlow(data) {
  if (!data.events.length) {
    sleep(5);
    return;
  }

  group('booking', () => {
    const event = data.events[Math.floor(Math.random() * data.events.length)];

    // Register + Login
    const email = `mixed_${__VU}_${__ITER}_${Date.now()}@test.com`;
    http.post(
      `${BASE_URL}/api/v1/auth/register`,
      JSON.stringify({ email, password: 'Test1234!', name: `User ${__VU}` }),
      { headers: DEFAULT_HEADERS }
    );
    sleep(0.5);

    const loginRes = http.post(
      `${BASE_URL}/api/v1/auth/login`,
      JSON.stringify({ email, password: 'Test1234!' }),
      { headers: DEFAULT_HEADERS }
    );
    if (loginRes.status !== 200) {
      sleep(3);
      return;
    }
    sleep(1);

    // Browse event
    http.get(`${BASE_URL}/api/v1/events/${event.id}`);
    sleep(1);

    // Join queue
    http.post(`${BASE_URL}/api/v1/queue/check/${event.id}`, null, {
      headers: DEFAULT_HEADERS,
    });
    sleep(2);

    // Check queue status
    http.get(`${BASE_URL}/api/v1/queue/status/${event.id}`);
    sleep(1);

    // View seats
    const seatsRes = http.get(`${BASE_URL}/api/v1/seats/events/${event.id}`);
    sleep(1);

    // Attempt reservation (ticket-type based for standing events)
    let ticketTypes = [];
    try {
      const eventDetail = JSON.parse(
        http.get(`${BASE_URL}/api/v1/events/${event.id}`).body
      );
      ticketTypes = eventDetail.ticket_types || eventDetail.ticketTypes || [];
    } catch {
      // skip
    }

    if (ticketTypes.length > 0) {
      http.post(
        `${BASE_URL}/api/v1/reservations`,
        JSON.stringify({
          eventId: event.id,
          items: [{ ticketTypeId: ticketTypes[0].id, quantity: 1 }],
        }),
        { headers: DEFAULT_HEADERS }
      );
    }
  });

  sleep(Math.random() * 5 + 3);
}

// 10% of traffic: admin dashboard + stats
export function adminFlow() {
  group('admin', () => {
    // Stats overview
    const statsRes = http.get(`${BASE_URL}/api/v1/stats/overview`);
    check(statsRes, { 'stats ok': (r) => r.status === 200 || r.status === 401 });
    sleep(5);

    // Daily stats
    http.get(`${BASE_URL}/api/v1/stats/daily?days=7`);
    sleep(3);

    // Revenue
    http.get(`${BASE_URL}/api/v1/stats/revenue`);
    sleep(3);

    // Performance
    http.get(`${BASE_URL}/api/v1/stats/performance`);
    sleep(5);
  });

  sleep(10);
}
