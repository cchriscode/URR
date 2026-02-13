import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { BASE_URL, DEFAULT_HEADERS } from '../lib/config.js';

const queueJoinDuration = new Trend('queue_join_duration');
const queueJoinSuccess = new Counter('queue_join_success');
const queueJoinFailed = new Counter('queue_join_failed');

export const options = {
  scenarios: {
    queue_rush: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '2m', target: 15000 },  // ramp to 15K
        { duration: '5m', target: 15000 },  // hold at 15K
        { duration: '1m', target: 0 },       // ramp down
      ],
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<5000', 'p(99)<10000'],
    http_req_failed: ['rate<0.05'],
    queue_join_duration: ['p(95)<3000'],
  },
};

const EVENT_ID = __ENV.EVENT_ID || '';

export function setup() {
  if (!EVENT_ID) {
    // Auto-discover first event
    const res = http.get(`${BASE_URL}/api/v1/events`);
    try {
      const events = JSON.parse(res.body).events || [];
      if (events.length > 0) {
        return { eventId: events[0].id };
      }
    } catch {
      // fallback
    }
    console.warn('No EVENT_ID provided and auto-discovery failed');
    return { eventId: null };
  }
  return { eventId: EVENT_ID };
}

export default function (data) {
  if (!data.eventId) {
    sleep(1);
    return;
  }

  // Simulate user joining queue
  const start = Date.now();
  const joinRes = http.post(
    `${BASE_URL}/api/v1/queue/check/${data.eventId}`,
    null,
    { headers: DEFAULT_HEADERS, tags: { name: 'queue_join' } }
  );
  const duration = Date.now() - start;
  queueJoinDuration.add(duration);

  const ok = check(joinRes, {
    'queue join ok': (r) => r.status === 200 || r.status === 202,
  });

  if (ok) {
    queueJoinSuccess.add(1);
  } else {
    queueJoinFailed.add(1);
  }
  sleep(Math.random() * 2 + 1); // 1-3s random think time

  // Poll status
  const statusRes = http.get(
    `${BASE_URL}/api/v1/queue/status/${data.eventId}`,
    { tags: { name: 'queue_status' } }
  );
  check(statusRes, {
    'queue status ok': (r) => r.status === 200,
  });
  sleep(Math.random() * 3 + 2); // 2-5s

  // Heartbeat
  http.post(
    `${BASE_URL}/api/v1/queue/heartbeat/${data.eventId}`,
    null,
    { headers: DEFAULT_HEADERS, tags: { name: 'queue_heartbeat' } }
  );
  sleep(Math.random() * 5 + 5); // 5-10s between heartbeats
}
