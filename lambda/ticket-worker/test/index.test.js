const { describe, it, beforeEach } = require('node:test');
const assert = require('node:assert/strict');

// We need to mock axios before requiring the handler.
// The handler calls axios.create() at module load time, so we
// intercept the module cache to inject our mock client.

let mockPostFn;
let handler;

// Build a fake axios module whose .create() returns our mock client
function buildMockAxios() {
  mockPostFn = async () => ({ status: 200, data: {} });

  const fakeClient = {
    post: (...args) => mockPostFn(...args),
  };

  const fakeAxios = {
    create: () => fakeClient,
  };

  return fakeAxios;
}

// Replace axios in require cache before loading the handler
const axiosId = require.resolve('axios');
require.cache[axiosId] = {
  id: axiosId,
  filename: axiosId,
  loaded: true,
  exports: buildMockAxios(),
};

handler = require('../index').handler;

describe('ticket-worker SQS handler', () => {
  beforeEach(() => {
    // Reset mock to default success
    mockPostFn = async () => ({ status: 200, data: {} });
  });

  it('returns empty batchItemFailures for empty records', async () => {
    const result = await handler({ Records: [] });
    assert.deepStrictEqual(result, { batchItemFailures: [] });
  });

  it('returns empty batchItemFailures for missing Records', async () => {
    const result = await handler({});
    assert.deepStrictEqual(result, { batchItemFailures: [] });
  });

  it('calls POST /internal/seats/reserve for seat_reserve action', async () => {
    let capturedUrl;
    let capturedPayload;
    mockPostFn = async (url, payload) => {
      capturedUrl = url;
      capturedPayload = payload;
      return { status: 200, data: {} };
    };

    const event = {
      Records: [
        {
          messageId: 'msg-1',
          body: JSON.stringify({
            action: 'seat_reserve',
            eventId: 'evt-1',
            userId: 'user-1',
            seatIds: ['A1', 'A2'],
            entryToken: 'tok-123',
          }),
        },
      ],
    };

    const result = await handler(event);

    assert.deepStrictEqual(result.batchItemFailures, []);
    assert.strictEqual(capturedUrl, '/internal/seats/reserve');
    assert.deepStrictEqual(capturedPayload, {
      eventId: 'evt-1',
      userId: 'user-1',
      seatIds: ['A1', 'A2'],
      entryToken: 'tok-123',
    });
  });

  it('calls POST /internal/reservations for reservation_create action', async () => {
    let capturedUrl;
    let capturedPayload;
    mockPostFn = async (url, payload) => {
      capturedUrl = url;
      capturedPayload = payload;
      return { status: 200, data: {} };
    };

    const event = {
      Records: [
        {
          messageId: 'msg-2',
          body: JSON.stringify({
            action: 'reservation_create',
            eventId: 'evt-2',
            userId: 'user-2',
            items: [{ seatId: 'B1', ticketType: 'VIP' }],
            entryToken: 'tok-456',
          }),
        },
      ],
    };

    const result = await handler(event);

    assert.deepStrictEqual(result.batchItemFailures, []);
    assert.strictEqual(capturedUrl, '/internal/reservations');
    assert.deepStrictEqual(capturedPayload, {
      eventId: 'evt-2',
      userId: 'user-2',
      items: [{ seatId: 'B1', ticketType: 'VIP' }],
      entryToken: 'tok-456',
    });
  });

  it('logs only for admitted action, no HTTP call', async () => {
    let postCalled = false;
    mockPostFn = async () => {
      postCalled = true;
      return { status: 200, data: {} };
    };

    const event = {
      Records: [
        {
          messageId: 'msg-3',
          body: JSON.stringify({
            action: 'admitted',
            eventId: 'evt-3',
            userId: 'user-3',
          }),
        },
      ],
    };

    const result = await handler(event);

    assert.deepStrictEqual(result.batchItemFailures, []);
    assert.strictEqual(postCalled, false);
  });

  it('skips unknown action without failure', async () => {
    let postCalled = false;
    mockPostFn = async () => {
      postCalled = true;
      return { status: 200, data: {} };
    };

    const event = {
      Records: [
        {
          messageId: 'msg-4',
          body: JSON.stringify({
            action: 'unknown_action',
            eventId: 'evt-4',
            userId: 'user-4',
          }),
        },
      ],
    };

    const result = await handler(event);

    assert.deepStrictEqual(result.batchItemFailures, []);
    assert.strictEqual(postCalled, false);
  });

  it('returns messageId in batchItemFailures on HTTP error', async () => {
    mockPostFn = async () => {
      throw new Error('Request failed with status code 500');
    };

    const event = {
      Records: [
        {
          messageId: 'msg-fail-1',
          body: JSON.stringify({
            action: 'seat_reserve',
            eventId: 'evt-5',
            userId: 'user-5',
            seatIds: ['C1'],
            entryToken: 'tok-789',
          }),
        },
      ],
    };

    const result = await handler(event);

    assert.strictEqual(result.batchItemFailures.length, 1);
    assert.strictEqual(result.batchItemFailures[0].itemIdentifier, 'msg-fail-1');
  });

  it('returns only failed messageId when multiple records with one failure', async () => {
    let callCount = 0;
    mockPostFn = async (url) => {
      callCount++;
      if (callCount === 2) {
        throw new Error('Request failed with status code 503');
      }
      return { status: 200, data: {} };
    };

    const event = {
      Records: [
        {
          messageId: 'msg-ok-1',
          body: JSON.stringify({
            action: 'seat_reserve',
            eventId: 'evt-6',
            userId: 'user-6',
            seatIds: ['D1'],
            entryToken: 'tok-a',
          }),
        },
        {
          messageId: 'msg-fail-2',
          body: JSON.stringify({
            action: 'reservation_create',
            eventId: 'evt-7',
            userId: 'user-7',
            items: [{ seatId: 'D2' }],
            entryToken: 'tok-b',
          }),
        },
        {
          messageId: 'msg-ok-3',
          body: JSON.stringify({
            action: 'admitted',
            eventId: 'evt-8',
            userId: 'user-8',
          }),
        },
      ],
    };

    const result = await handler(event);

    assert.strictEqual(result.batchItemFailures.length, 1);
    assert.strictEqual(result.batchItemFailures[0].itemIdentifier, 'msg-fail-2');
  });
});
