const { describe, it, before } = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('crypto');

const TEST_SECRET = 'test-secret-key-for-unit-tests-min-32-chars';

// Set env before requiring handler (handler validates on load)
process.env.QUEUE_ENTRY_TOKEN_SECRET = TEST_SECRET;

const { handler } = require('../index');

const SECRET = TEST_SECRET;

/**
 * Create a valid HMAC-SHA256 JWT token.
 * @param {object} payloadOverrides - fields to merge into the payload
 * @returns {string} compact JWT string
 */
function createJWT(payloadOverrides = {}) {
  const header = { alg: 'HS256', typ: 'JWT' };
  const now = Math.floor(Date.now() / 1000);
  const payload = {
    sub: 'test-event-id',
    iat: now,
    exp: now + 600, // 10 minutes from now
    ...payloadOverrides,
  };

  const headerB64 = Buffer.from(JSON.stringify(header)).toString('base64url');
  const payloadB64 = Buffer.from(JSON.stringify(payload)).toString('base64url');
  const data = `${headerB64}.${payloadB64}`;
  const signature = crypto
    .createHmac('sha256', SECRET)
    .update(data)
    .digest('base64url');

  return `${headerB64}.${payloadB64}.${signature}`;
}

/**
 * Create an expired JWT token.
 */
function createExpiredJWT() {
  const pastTime = Math.floor(Date.now() / 1000) - 3600; // 1 hour ago
  return createJWT({ iat: pastTime - 600, exp: pastTime });
}

/**
 * Build a CloudFront viewer-request event structure.
 */
function buildCFEvent(uri, headers = {}) {
  return {
    Records: [
      {
        cf: {
          request: {
            uri,
            headers: { ...headers },
          },
        },
      },
    ],
  };
}

describe('Lambda@Edge edge-queue-check', () => {
  it('bypass path /api/queue/check returns request unchanged', async () => {
    const event = buildCFEvent('/api/queue/check');
    const result = await handler(event);

    assert.strictEqual(result.uri, '/api/queue/check');
    assert.strictEqual(result.status, undefined); // No redirect
  });

  it('protected path without token returns 302 redirect', async () => {
    const event = buildCFEvent('/api/reservations/create');
    const result = await handler(event);

    assert.strictEqual(result.status, '302');
    assert.ok(result.headers['location']);
    assert.ok(result.headers['cache-control']);
  });

  it('protected path with valid cookie token returns request', async () => {
    const token = createJWT();
    const event = buildCFEvent('/api/seats/reserve', {
      cookie: [{ value: `tiketi-entry-token=${token}; other=abc` }],
    });

    const result = await handler(event);

    assert.strictEqual(result.uri, '/api/seats/reserve');
    assert.strictEqual(result.status, undefined);
  });

  it('protected path with valid header token (x-queue-entry-token) returns request', async () => {
    const token = createJWT();
    const event = buildCFEvent('/api/tickets/purchase', {
      'x-queue-entry-token': [{ value: token }],
    });

    const result = await handler(event);

    assert.strictEqual(result.uri, '/api/tickets/purchase');
    assert.strictEqual(result.status, undefined);
  });

  it('protected path with expired token returns 302 redirect', async () => {
    const token = createExpiredJWT();
    const event = buildCFEvent('/api/reservations/create', {
      cookie: [{ value: `tiketi-entry-token=${token}` }],
    });

    const result = await handler(event);

    assert.strictEqual(result.status, '302');
  });

  it('non-protected path returns request unchanged', async () => {
    const event = buildCFEvent('/api/events/123');
    const result = await handler(event);

    assert.strictEqual(result.uri, '/api/events/123');
    assert.strictEqual(result.status, undefined);
  });

  it('health path returns request unchanged', async () => {
    const event = buildCFEvent('/health');
    const result = await handler(event);

    assert.strictEqual(result.uri, '/health');
    assert.strictEqual(result.status, undefined);
  });

  it('header fallback is used when cookie is absent', async () => {
    const token = createJWT();
    // No cookie header, only x-queue-entry-token
    const event = buildCFEvent('/api/admin/settings', {
      'x-queue-entry-token': [{ value: token }],
    });

    const result = await handler(event);

    assert.strictEqual(result.uri, '/api/admin/settings');
    assert.strictEqual(result.status, undefined);
  });

  it('invalid JWT signature returns 302 redirect', async () => {
    const token = createJWT();
    // Tamper with the signature
    const parts = token.split('.');
    parts[2] = 'invalidsignature';
    const tamperedToken = parts.join('.');

    const event = buildCFEvent('/api/seats/reserve', {
      cookie: [{ value: `tiketi-entry-token=${tamperedToken}` }],
    });

    const result = await handler(event);

    assert.strictEqual(result.status, '302');
  });
});
