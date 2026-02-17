'use strict';

const { handler: assignHandler } = require('./handlers/assign');
const { handler: checkHandler } = require('./handlers/check');
const { handler: statusHandler } = require('./handlers/status');

const CORS_ORIGIN = process.env.CORS_ORIGIN;
if (!CORS_ORIGIN) throw new Error('CORS_ORIGIN environment variable is required');

const CORS_HEADERS = {
  'Access-Control-Allow-Origin': CORS_ORIGIN,
  'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type',
  'Content-Type': 'application/json',
};

/**
 * Lambda handler — routes API Gateway requests to the correct handler.
 * Single Lambda for all VWR API endpoints to minimize cold starts.
 */
exports.handler = async (event) => {
  // Handle CORS preflight
  if (event.httpMethod === 'OPTIONS') {
    return { statusCode: 204, headers: CORS_HEADERS, body: '' };
  }

  try {
    const path = event.pathParameters || {};
    const method = event.httpMethod;
    const resource = event.resource;
    const body = event.body ? JSON.parse(event.body) : {};
    const query = event.queryStringParameters || {};

    let result;

    // Rate limiting: Configure API Gateway throttling per IP
    // Settings: 10 requests/second burst, 5 requests/second sustained per IP
    // This prevents mass queue position acquisition from a single source

    if (resource === '/vwr/assign/{eventId}' && method === 'POST') {
      result = await assignHandler(path.eventId, body);
    } else if (resource === '/vwr/check/{eventId}/{requestId}' && method === 'GET') {
      result = await checkHandler(path.eventId, path.requestId, query);
    } else if (resource === '/vwr/status/{eventId}' && method === 'GET') {
      result = await statusHandler(path.eventId);
    } else {
      result = { statusCode: 404, body: { error: 'Not found' } };
    }

    return {
      statusCode: result.statusCode,
      headers: CORS_HEADERS,
      body: JSON.stringify(result.body),
    };
  } catch (err) {
    console.error('VWR API error:', err);
    return {
      statusCode: 500,
      headers: CORS_HEADERS,
      body: JSON.stringify({ error: 'Internal server error' }),
    };
  }
};
