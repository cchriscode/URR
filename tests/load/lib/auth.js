import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL, DEFAULT_HEADERS, TEST_USER } from './config.js';

export function login(email, password) {
  const res = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ email: email || TEST_USER.email, password: password || TEST_USER.password }),
    { headers: DEFAULT_HEADERS }
  );

  check(res, {
    'login status 200': (r) => r.status === 200,
  });

  return res;
}

export function register(suffix) {
  const uniqueEmail = `loadtest_${suffix}_${Date.now()}@example.com`;
  const res = http.post(
    `${BASE_URL}/api/v1/auth/register`,
    JSON.stringify({
      email: uniqueEmail,
      password: 'LoadTest123!',
      name: `Load Tester ${suffix}`,
    }),
    { headers: DEFAULT_HEADERS }
  );

  return { res, email: uniqueEmail };
}

export function getAuthCookieJar(email, password) {
  const jar = http.cookieJar();
  login(email, password);
  return jar;
}
