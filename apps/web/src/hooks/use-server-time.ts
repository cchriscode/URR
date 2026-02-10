"use client";

import { useEffect, useState } from "react";

function resolveBaseUrl() {
  if (process.env.NEXT_PUBLIC_API_URL) return process.env.NEXT_PUBLIC_API_URL;
  if (typeof window === "undefined") return "http://localhost:3001";
  const hostname = window.location.hostname;
  if (hostname === "localhost" || hostname === "127.0.0.1") return "http://localhost:3001";
  if (/^(172\.|192\.168\.|10\.)/.test(hostname)) return `http://${hostname}:3001`;
  return "";
}

let cachedOffset: number | null = null;
let fetchPromise: Promise<number> | null = null;

async function fetchOffset(): Promise<number> {
  if (cachedOffset !== null) return cachedOffset;
  if (fetchPromise) return fetchPromise;

  fetchPromise = (async () => {
    try {
      const before = Date.now();
      const res = await fetch(`${resolveBaseUrl()}/api/time`);
      const after = Date.now();
      const data = await res.json();
      const rtt = after - before;
      const serverTime = new Date(data.time).getTime();
      const clientMidpoint = before + rtt / 2;
      cachedOffset = serverTime - clientMidpoint;
      return cachedOffset;
    } catch {
      cachedOffset = 0;
      return 0;
    }
  })();

  return fetchPromise;
}

export function useServerTime() {
  const [offset, setOffset] = useState<number>(cachedOffset ?? 0);
  const [ready, setReady] = useState(cachedOffset !== null);

  useEffect(() => {
    fetchOffset().then((o) => {
      setOffset(o);
      setReady(true);
    });
  }, []);

  return { offset, ready };
}

export function getServerNow(offset: number): number {
  return Date.now() + offset;
}
