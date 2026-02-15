'use strict';

const { assignPosition, getEventStatus } = require('../lib/dynamo');
const crypto = require('crypto');

/**
 * POST /vwr/assign/{eventId}
 * Assigns a queue position to the user. Returns requestId + position.
 */
async function handler(eventId, body) {
  const userId = body?.userId || 'anonymous';

  // Check if VWR is active for this event
  const status = await getEventStatus(eventId);
  if (!status || !status.isActive) {
    return {
      statusCode: 404,
      body: { error: 'VWR is not active for this event' },
    };
  }

  // Generate unique requestId (stored client-side for polling)
  const requestId = crypto.randomUUID();

  try {
    const position = await assignPosition(eventId, requestId, userId);

    const totalAhead = position - (status.servingCounter || 0);
    // Rough estimate: 500 per 10s batch = 50/sec
    const estimatedWaitSeconds = Math.max(0, Math.ceil(totalAhead / 50));

    return {
      statusCode: 200,
      body: {
        requestId,
        position,
        estimatedWait: estimatedWaitSeconds,
        servingCounter: status.servingCounter || 0,
      },
    };
  } catch (err) {
    if (err.name === 'ConditionalCheckFailedException') {
      return {
        statusCode: 404,
        body: { error: 'VWR is not active for this event' },
      };
    }
    throw err;
  }
}

module.exports = { handler };
