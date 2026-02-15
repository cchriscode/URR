'use strict';

const { getEventStatus } = require('../lib/dynamo');

/**
 * GET /vwr/status/{eventId}
 * Returns the current VWR status for an event (public, no auth required).
 */
async function handler(eventId) {
  const status = await getEventStatus(eventId);

  if (!status) {
    return {
      statusCode: 404,
      body: { error: 'VWR not found for this event' },
    };
  }

  return {
    statusCode: 200,
    body: {
      eventId,
      isActive: status.isActive,
      totalInQueue: status.nextPosition,
      serving: status.servingCounter,
      waitingCount: Math.max(0, status.nextPosition - status.servingCounter),
    },
  };
}

module.exports = { handler };
