export const BASE_URL = __ENV.BASE_URL || 'http://localhost:30080';

export const DEFAULT_HEADERS = {
  'Content-Type': 'application/json',
};

export const THRESHOLDS = {
  http_req_duration: ['p(95)<3000', 'p(99)<5000'],
  http_req_failed: ['rate<0.01'],
};

// Test user credentials (must exist in the system)
export const TEST_USER = {
  email: __ENV.TEST_EMAIL || 'loadtest@example.com',
  password: __ENV.TEST_PASSWORD || 'LoadTest123!',
  name: 'Load Test User',
};

// Seed event IDs (set via env or auto-discover)
export const EVENT_ID = __ENV.EVENT_ID || '';
