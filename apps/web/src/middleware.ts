import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";

export function middleware(request: NextRequest) {
  const nonce = Buffer.from(crypto.randomUUID()).toString("base64");

  // Next.js generates inline scripts (RSC payload) at build/prerender time
  // that cannot carry nonces, so script-src must allow 'unsafe-inline'.
  // style-src uses nonce to avoid unsafe-inline for injected styles.
  const cspHeader = [
    "default-src 'self'",
    "script-src 'self' 'unsafe-inline' https://accounts.google.com https://apis.google.com",
    `style-src 'self' 'unsafe-inline' 'nonce-${nonce}' https://accounts.google.com`,
    "img-src 'self' data: https:",
    "connect-src 'self' http://localhost:* https://*.tiketi.com https://accounts.google.com",
    "frame-src https://accounts.google.com",
    "frame-ancestors 'none'",
  ].join("; ");

  const requestHeaders = new Headers(request.headers);
  requestHeaders.set("x-nonce", nonce);

  const response = NextResponse.next({ request: { headers: requestHeaders } });
  response.headers.set("Content-Security-Policy", cspHeader);
  response.headers.set("X-Frame-Options", "DENY");
  response.headers.set("X-Content-Type-Options", "nosniff");
  response.headers.set("Referrer-Policy", "strict-origin-when-cross-origin");
  response.headers.set("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=()");

  return response;
}

export const config = {
  matcher: [
    { source: "/((?!_next/static|_next/image|favicon.ico).*)", missing: [{ type: "header", key: "next-router-prefetch" }] },
  ],
};
