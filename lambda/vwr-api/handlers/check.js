'use strict';

const { getPositionAndCounter } = require('../lib/dynamo');
const { createToken } = require('../lib/token');

/**
 * GET /vwr/check/{eventId}/{requestId}
 * Checks if the user's position has been reached by the serving counter.
 * If admitted, issues a Tier 1 VWR JWT token.
 */
async function handler(eventId, requestId, queryParams) {
  const { position, servingCounter, nextPosition, isActive } =
    await getPositionAndCounter(eventId, requestId);

  if (position === null) {
    return {
      statusCode: 404,
      body: { error: 'Position not found. Request a new position.' },
    };
  }

  if (!isActive) {
    // VWR deactivated — admit everyone
    const token = createToken(eventId, queryParams?.userId || 'anonymous');
    return {
      statusCode: 200,
      body: { admitted: true, token, position, servingCounter },
    };
  }

  const admitted = position <= servingCounter;

  const response = {
    admitted,
    position,
    servingCounter,
    totalInQueue: nextPosition,
    ahead: Math.max(0, position - servingCounter),
  };

  if (admitted) {
    response.token = createToken(eventId, queryParams?.userId || 'anonymous');
  } else {
    // Estimate wait: 500 per 10s batch = 50/sec
    response.estimatedWait = Math.max(0, Math.ceil((position - servingCounter) / 50));
    // Poll interval based on distance from serving counter
    const ahead = position - servingCounter;
    if (ahead <= 500) response.nextPoll = 2;
    else if (ahead <= 2000) response.nextPoll = 5;
    else if (ahead <= 10000) response.nextPoll = 10;
    else response.nextPoll = 15;
  }

  return { statusCode: 200, body: response };
}

module.exports = { handler };
