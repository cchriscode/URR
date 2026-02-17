'use strict';

const { DynamoDBClient } = require('@aws-sdk/client-dynamodb');
const { DynamoDBDocumentClient, UpdateCommand, PutCommand, GetCommand } = require('@aws-sdk/lib-dynamodb');

const client = new DynamoDBClient({});
const ddb = DynamoDBDocumentClient.from(client);

const TABLE_COUNTERS = process.env.TABLE_COUNTERS;
const TABLE_POSITIONS = process.env.TABLE_POSITIONS;

/**
 * Atomically increment nextPosition and return the assigned position.
 * ConditionExpression ensures the event VWR is active.
 */
async function assignPosition(eventId, requestId, userId) {
  // Atomic counter increment
  const counterResult = await ddb.send(new UpdateCommand({
    TableName: TABLE_COUNTERS,
    Key: { eventId },
    UpdateExpression: 'ADD nextPosition :one SET updatedAt = :now',
    ConditionExpression: 'isActive = :true',
    ExpressionAttributeValues: {
      ':one': 1,
      ':true': true,
      ':now': Date.now(),
    },
    ReturnValues: 'UPDATED_NEW',
  }));

  const position = counterResult.Attributes.nextPosition;

  // Record the position
  await ddb.send(new PutCommand({
    TableName: TABLE_POSITIONS,
    Item: {
      eventId,
      requestId,
      position,
      userId: userId || 'anonymous',
      createdAt: Date.now(),
      ttl: Math.floor(Date.now() / 1000) + 86400, // 24h TTL
    },
  }));

  return position;
}

/**
 * Get a user's position and the current serving counter.
 */
async function getPositionAndCounter(eventId, requestId) {
  const [posResult, counterResult] = await Promise.all([
    ddb.send(new GetCommand({
      TableName: TABLE_POSITIONS,
      Key: { eventId, requestId },
    })),
    ddb.send(new GetCommand({
      TableName: TABLE_COUNTERS,
      Key: { eventId },
    })),
  ]);

  return {
    position: posResult.Item?.position ?? null,
    servingCounter: counterResult.Item?.servingCounter ?? 0,
    nextPosition: counterResult.Item?.nextPosition ?? 0,
    isActive: counterResult.Item?.isActive ?? false,
  };
}

/**
 * Get event VWR status (counters only).
 */
async function getEventStatus(eventId) {
  const result = await ddb.send(new GetCommand({
    TableName: TABLE_COUNTERS,
    Key: { eventId },
  }));

  if (!result.Item) return null;

  return {
    nextPosition: result.Item.nextPosition || 0,
    servingCounter: result.Item.servingCounter || 0,
    isActive: result.Item.isActive || false,
    updatedAt: result.Item.updatedAt || 0,
  };
}

module.exports = { assignPosition, getPositionAndCounter, getEventStatus };
