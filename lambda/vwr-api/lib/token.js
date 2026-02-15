'use strict';

const crypto = require('crypto');

const VWR_TOKEN_SECRET = process.env.VWR_TOKEN_SECRET;

/**
 * Create a Tier 1 VWR JWT token.
 * Minimal JWT implementation (no external deps) for Lambda cold-start optimization.
 */
function createToken(eventId, userId) {
  const header = { alg: 'HS256', typ: 'JWT' };
  const now = Math.floor(Date.now() / 1000);
  const payload = {
    sub: eventId,
    uid: userId || 'anonymous',
    tier: 1,
    iat: now,
    exp: now + 600, // 10 minutes
  };

  const encodedHeader = base64url(JSON.stringify(header));
  const encodedPayload = base64url(JSON.stringify(payload));
  const signature = sign(`${encodedHeader}.${encodedPayload}`);

  return `${encodedHeader}.${encodedPayload}.${signature}`;
}

/**
 * Verify a Tier 1 VWR JWT token. Returns payload or null.
 */
function verifyToken(token) {
  try {
    const parts = token.split('.');
    if (parts.length !== 3) return null;

    const [encodedHeader, encodedPayload, signature] = parts;
    const expectedSig = sign(`${encodedHeader}.${encodedPayload}`);

    const sigBuf = Buffer.from(signature);
    const expectedBuf = Buffer.from(expectedSig);
    if (sigBuf.length !== expectedBuf.length || !crypto.timingSafeEqual(sigBuf, expectedBuf)) {
      return null;
    }

    const payload = JSON.parse(Buffer.from(encodedPayload, 'base64url').toString());

    if (payload.exp < Math.floor(Date.now() / 1000)) return null;
    if (payload.tier !== 1) return null;

    return payload;
  } catch {
    return null;
  }
}

function base64url(str) {
  return Buffer.from(str).toString('base64url');
}

function sign(data) {
  return crypto
    .createHmac('sha256', VWR_TOKEN_SECRET)
    .update(data)
    .digest('base64url');
}

module.exports = { createToken, verifyToken };
