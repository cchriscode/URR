'use strict';

/**
 * VWR configuration loader for Lambda@Edge.
 * Reads vwr-config.json from the same deployment package (baked at build time).
 * Falls back to empty config if not present.
 *
 * In production, this config is updated by the admin API and redeployed.
 * The 5-minute cache is for future S3-based dynamic config loading.
 */

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
    // Load from bundled config file (built by Terraform / admin API)
    const config = require('./vwr-active.json');
    cachedConfig = config;
    cacheExpiry = now + CACHE_TTL_MS;
    return config;
  } catch (e) {
    // No VWR config — VWR is not active for any event
    cachedConfig = { activeEvents: [] };
    cacheExpiry = now + CACHE_TTL_MS;
    return cachedConfig;
  }
}

module.exports = { getVwrConfig };
