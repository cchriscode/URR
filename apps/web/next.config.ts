import type { NextConfig } from "next";
import path from "node:path";

const nextConfig: NextConfig = {
  turbopack: {
    root: path.resolve(__dirname),
  },
  images: {
    remotePatterns: [
      { protocol: "https", hostname: "**" },
    ],
  },
  // Security headers (CSP, X-Frame-Options, etc.) are set dynamically
  // by middleware.ts with per-request nonce for script/style sources.
};

export default nextConfig;
