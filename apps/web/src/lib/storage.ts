"use client";

/**
 * clearAuth is called by the api-client interceptor on 401 refresh failure.
 * User state is now managed by AuthProvider (auth-context.tsx),
 * so this just cleans up any legacy localStorage data.
 */
export function clearAuth(): void {
  if (typeof window === "undefined") return;
  localStorage.removeItem("user");
}
