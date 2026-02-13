"use client";

import { useEffect, useRef, useState } from "react";
import { queueApi } from "@/lib/api-client";
import type { QueueStatus } from "@/lib/types";

const DEFAULT_POLL_SECONDS = 3;
const MIN_POLL_SECONDS = 1;
const MAX_POLL_SECONDS = 60;

function clampPoll(seconds: number): number {
  return Math.max(MIN_POLL_SECONDS, Math.min(MAX_POLL_SECONDS, seconds));
}

export function useQueuePolling(eventId: string, enabled = true) {
  const [status, setStatus] = useState<QueueStatus | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const mountedRef = useRef(true);
  const pollIntervalRef = useRef(DEFAULT_POLL_SECONDS);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);

  useEffect(() => {
    if (!enabled || !eventId) {
      return;
    }

    let timer: ReturnType<typeof setTimeout> | null = null;

    const poll = async () => {
      try {
        setLoading(true);
        const { data } = await queueApi.status(eventId);
        if (mountedRef.current) {
          setStatus(data);
          setError(null);
          // Update polling interval from server response
          if (data.nextPoll != null) {
            pollIntervalRef.current = clampPoll(data.nextPoll);
          }
          // Store entry token as cookie for Lambda@Edge verification
          if (data.entryToken) {
            const isSecure = window.location.protocol === 'https:';
            document.cookie = `tiketi-entry-token=${data.entryToken}; path=/; max-age=600; SameSite=Strict${isSecure ? '; Secure' : ''}`;
          }
        }
      } catch {
        if (mountedRef.current) {
          setError("Queue status polling failed");
        }
      } finally {
        if (mountedRef.current) {
          setLoading(false);
          timer = setTimeout(poll, pollIntervalRef.current * 1000);
        }
      }
    };

    poll();

    return () => {
      if (timer) clearTimeout(timer);
    };
  }, [enabled, eventId]);

  return { status, loading, error };
}
