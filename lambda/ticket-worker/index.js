/**
 * Lambda Worker for SQS FIFO Ticket Processing
 *
 * Consumes messages from SQS FIFO queue and calls ticket-service internal APIs.
 * Uses ReportBatchItemFailures pattern — only failed messages are retried.
 *
 * Environment Variables:
 * - TICKET_SERVICE_URL: ticket-service base URL (e.g. http://ticket-service:3002)
 * - INTERNAL_API_TOKEN: shared secret for internal service auth
 */

const axios = require('axios');

const TICKET_SERVICE_URL = process.env.TICKET_SERVICE_URL || 'http://localhost:3002';
const INTERNAL_API_TOKEN = process.env.INTERNAL_API_TOKEN;
if (!INTERNAL_API_TOKEN) {
  throw new Error('INTERNAL_API_TOKEN environment variable is required');
}
const REQUEST_TIMEOUT_MS = 10_000;

const client = axios.create({
  baseURL: TICKET_SERVICE_URL,
  timeout: REQUEST_TIMEOUT_MS,
  headers: {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${INTERNAL_API_TOKEN}`,
  },
});

exports.handler = async (event) => {
  const batchItemFailures = [];

  if (!event.Records || event.Records.length === 0) {
    return { batchItemFailures };
  }

  for (const record of event.Records) {
    try {
      const body = JSON.parse(record.body);
      await processMessage(body);
    } catch (err) {
      console.error(
        `Failed to process message ${record.messageId}:`,
        err.message || err
      );
      batchItemFailures.push({
        itemIdentifier: record.messageId,
      });
    }
  }

  return { batchItemFailures };
};

async function processMessage(message) {
  const { action, eventId, userId, seatIds, entryToken, items } = message;

  switch (action) {
    case 'seat_reserve':
      if (!eventId || !userId || !seatIds || seatIds.length === 0) {
        throw new Error(`Invalid seat_reserve payload: missing required fields`);
      }
      await client.post('/internal/seats/reserve', {
        eventId,
        userId,
        seatIds,
        entryToken,
      });
      console.log(`seat_reserve OK: user=${userId} event=${eventId} seats=${seatIds.length}`);
      break;

    case 'reservation_create':
      if (!eventId || !userId || !items || items.length === 0) {
        throw new Error(`Invalid reservation_create payload: missing required fields`);
      }
      await client.post('/internal/reservations', {
        eventId,
        userId,
        items,
        entryToken,
      });
      console.log(`reservation_create OK: user=${userId} event=${eventId}`);
      break;

    case 'admitted':
      // Admission notification — log only, no action needed.
      // The entry token is already issued by queue-service.
      console.log(`admitted: user=${userId} event=${eventId}`);
      break;

    default:
      console.warn(`Unknown action: ${action}, skipping message`);
  }
}
