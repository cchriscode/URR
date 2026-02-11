/**
 * Lambda@Edge function for CloudFront viewer-request
 * Validates queue entry tokens before allowing access to ticketing APIs
 *
 * Environment Variables:
 * - QUEUE_ENTRY_TOKEN_SECRET: HMAC-SHA256 secret key (must match ticket-service)
 *
 * Protected paths: /api/reservations/**, /api/tickets/**
 * Bypass paths: /api/queue/**, /api/auth/**, /api/events/**
 */

const crypto = require('crypto');

// Secret key should match queue.entry-token.secret in ticket-service
const SECRET = process.env.QUEUE_ENTRY_TOKEN_SECRET || 'dev-secret-key-change-in-production-min-32-chars';

// Paths that require valid entry token
const PROTECTED_PATHS = [
  '/api/reservations',
  '/api/tickets',
  '/api/seats',
  '/api/admin'
];

// Paths that don't require entry token
const BYPASS_PATHS = [
  '/api/queue',
  '/api/auth',
  '/api/events',
  '/api/stats',
  '/health',
  '/actuator'
];

exports.handler = async (event) => {
  const request = event.Records[0].cf.request;
  const uri = request.uri;

  // Check if path is protected
  const isProtected = PROTECTED_PATHS.some(path => uri.startsWith(path));
  const isBypassed = BYPASS_PATHS.some(path => uri.startsWith(path));

  if (!isProtected || isBypassed) {
    // Allow request to pass through
    return request;
  }

  // Extract entry token from cookie
  const cookies = request.headers.cookie || [];
  let entryToken = null;

  for (const cookieHeader of cookies) {
    const cookieValue = cookieHeader.value;
    const match = cookieValue.match(/tiketi-entry-token=([^;]+)/);
    if (match) {
      entryToken = match[1];
      break;
    }
  }

  if (!entryToken) {
    // No token found, redirect to queue page
    return redirectToQueue(request);
  }

  // Verify JWT
  const isValid = verifyJWT(entryToken, SECRET);

  if (!isValid) {
    // Invalid or expired token, redirect to queue page
    return redirectToQueue(request);
  }

  // Token is valid, allow request
  return request;
};

/**
 * Verify JWT signature and expiration
 */
function verifyJWT(token, secret) {
  try {
    const parts = token.split('.');
    if (parts.length !== 3) {
      return false;
    }

    const [headerB64, payloadB64, signatureB64] = parts;

    // Verify signature
    const data = `${headerB64}.${payloadB64}`;
    const expectedSignature = crypto
      .createHmac('sha256', secret)
      .update(data)
      .digest('base64url');

    if (signatureB64 !== expectedSignature) {
      return false;
    }

    // Check expiration
    const payload = JSON.parse(Buffer.from(payloadB64, 'base64url').toString('utf8'));
    const now = Math.floor(Date.now() / 1000);

    if (payload.exp && payload.exp < now) {
      return false;
    }

    return true;
  } catch (err) {
    console.error('JWT verification error:', err);
    return false;
  }
}

/**
 * Redirect to queue page
 */
function redirectToQueue(request) {
  // Extract eventId from path if possible
  const eventIdMatch = request.uri.match(/\/event\/([a-f0-9-]+)/i);
  const eventId = eventIdMatch ? eventIdMatch[1] : '';

  const redirectUri = eventId ? `/queue/${eventId}` : '/';

  return {
    status: '302',
    statusDescription: 'Found',
    headers: {
      'location': [{
        key: 'Location',
        value: redirectUri
      }],
      'cache-control': [{
        key: 'Cache-Control',
        value: 'no-store, no-cache, must-revalidate'
      }]
    }
  };
}
