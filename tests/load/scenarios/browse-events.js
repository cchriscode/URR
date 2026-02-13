import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, THRESHOLDS } from '../lib/config.js';

export const options = {
  stages: [
    { duration: '30s', target: 50 },
    { duration: '2m', target: 200 },
    { duration: '1m', target: 200 },
    { duration: '30s', target: 0 },
  ],
  thresholds: THRESHOLDS,
};

export default function () {
  // 1. List events
  const listRes = http.get(`${BASE_URL}/api/v1/events`);
  check(listRes, {
    'events list 200': (r) => r.status === 200,
    'has events': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.events && body.events.length > 0;
      } catch {
        return false;
      }
    },
  });
  sleep(1);

  // 2. View event detail (pick first event)
  let eventId;
  try {
    const events = JSON.parse(listRes.body).events;
    if (events && events.length > 0) {
      eventId = events[Math.floor(Math.random() * events.length)].id;
    }
  } catch {
    // skip detail if parse fails
  }

  if (eventId) {
    const detailRes = http.get(`${BASE_URL}/api/v1/events/${eventId}`);
    check(detailRes, {
      'event detail 200': (r) => r.status === 200,
    });
    sleep(0.5);

    // 3. View seats for event
    const seatsRes = http.get(`${BASE_URL}/api/v1/seats/events/${eventId}`);
    check(seatsRes, {
      'seats 200': (r) => r.status === 200 || r.status === 404,
    });
    sleep(0.5);
  }

  // 4. View artists
  const artistsRes = http.get(`${BASE_URL}/api/v1/artists`);
  check(artistsRes, {
    'artists 200': (r) => r.status === 200,
  });
  sleep(1);
}
