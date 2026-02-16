'use strict';

/**
 * VWR configuration loader for Lambda@Edge.
 *
 * Primary: Loads vwr-active.json from S3 with a 5-minute in-memory cache.
 * Fallback: Falls back to bundled vwr-active.json if S3 is unavailable.
 *
 * The S3-based approach allows dynamic updates without redeploying the Lambda.
 */

const { S3Client, GetObjectCommand } = require('@aws-sdk/client-s3');
const s3 = new S3Client({ region: 'us-east-1' });

let cachedConfig = null;
let cacheExpiry = 0;
const CACHE_TTL_MS = 5 * 60 * 1000; // 5 minutes

/**
 * Get the VWR configuration.
 * Returns { activeEvents: string[] } or null.
 */
async function getVwrConfig() {
  const now = Date.now();

  if (cachedConfig !== null && now < cacheExpiry) {
    return cachedConfig;
  }

  try {
    const command = new GetObjectCommand({
      Bucket: process.env.VWR_CONFIG_BUCKET || 'urr-vwr-config',
      Key: 'vwr-active.json',
    });
    const response = await s3.send(command);
    const body = await response.Body.transformToString();
    cachedConfig = JSON.parse(body);
    cacheExpiry = now + CACHE_TTL_MS;
    return cachedConfig;
  } catch (err) {
    console.error('Failed to load VWR config from S3:', err);
    // Return cached config even if expired, as fallback
    if (cachedConfig) return cachedConfig;

    // Final fallback: try bundled config file
    try {
      const config = require('./vwr-active.json');
      cachedConfig = config;
      cacheExpiry = now + CACHE_TTL_MS;
      return config;
    } catch (_e) {
      // No VWR config — VWR is not active for any event
      cachedConfig = { activeEvents: [] };
      cacheExpiry = now + CACHE_TTL_MS;
      return cachedConfig;
    }
  }
}

module.exports = { getVwrConfig };
