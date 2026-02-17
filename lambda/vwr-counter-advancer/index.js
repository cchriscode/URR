'use strict';

const { DynamoDBClient } = require('@aws-sdk/client-dynamodb');
const { DynamoDBDocumentClient, ScanCommand, UpdateCommand } = require('@aws-sdk/lib-dynamodb');

const client = new DynamoDBClient({});
const ddb = DynamoDBDocumentClient.from(client);

const TABLE_COUNTERS = process.env.TABLE_COUNTERS;
const BATCH_SIZE = parseInt(process.env.BATCH_SIZE || '500', 10);

// EventBridge minimum rate is 1 minute.
// This Lambda runs multiple advancement cycles within a single invocation
// to achieve ~10-second granularity.
const CYCLES_PER_INVOCATION = 6;  // 6 cycles x 10s = 60s
const CYCLE_INTERVAL_MS = 10000;   // 10 seconds

/**
 * Handler triggered by EventBridge every 1 minute.
 * Advances the serving counter for all active VWR events.
 */
exports.handler = async () => {
  for (let i = 0; i < CYCLES_PER_INVOCATION; i++) {
    const startTime = Date.now();

    try {
      // Find all active VWR events
      const result = await ddb.send(new ScanCommand({
        TableName: TABLE_COUNTERS,
        FilterExpression: 'isActive = :true',
        ExpressionAttributeValues: { ':true': true },
      }));

      const events = result.Items || [];
      if (events.length === 0) {
        console.log(`Cycle ${i + 1}/${CYCLES_PER_INVOCATION}: No active VWR events`);
      } else {
        // Advance each event's serving counter
        const results = await Promise.all(
          events.map(item => advanceCounter(item.eventId))
        );

        console.log(`Cycle ${i + 1}/${CYCLES_PER_INVOCATION}: Advanced ${results.length} events`,
          JSON.stringify(results));
      }
    } catch (err) {
      console.error(`Cycle ${i + 1}/${CYCLES_PER_INVOCATION}: Error`, err);
    }

    // Wait for next cycle (except on last iteration)
    if (i < CYCLES_PER_INVOCATION - 1) {
      const elapsed = Date.now() - startTime;
      const waitMs = Math.max(0, CYCLE_INTERVAL_MS - elapsed);
      if (waitMs > 0) {
        await new Promise(resolve => setTimeout(resolve, waitMs));
      }
    }
  }

  return { statusCode: 200, body: 'OK' };
};

async function advanceCounter(eventId) {
  try {
    const result = await ddb.send(new UpdateCommand({
      TableName: TABLE_COUNTERS,
      Key: { eventId },
      UpdateExpression: 'ADD servingCounter :batch SET updatedAt = :now',
      ConditionExpression: 'isActive = :true AND (attribute_not_exists(servingCounter) OR servingCounter < nextPosition)',
      ExpressionAttributeValues: {
        ':batch': BATCH_SIZE,
        ':true': true,
        ':now': Date.now(),
      },
      ReturnValues: 'UPDATED_NEW',
    }));

    return {
      eventId,
      servingCounter: result.Attributes.servingCounter,
      nextPosition: result.Attributes.nextPosition,
    };
  } catch (err) {
    if (err.name === 'ConditionalCheckFailedException') {
      // servingCounter already >= nextPosition or event deactivated
      return { eventId, status: 'caught_up_or_inactive' };
    }
    throw err;
  }
}
