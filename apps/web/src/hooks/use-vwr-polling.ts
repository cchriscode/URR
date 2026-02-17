"use client";

import { useEffect, useRef, useState, useCallback } from "react";
import { vwrApi } from "@/lib/api-client";
import type { VwrCheckResponse } from "@/lib/types";

const DEFAULT_POLL_SECONDS = 5;

function getAnonUserId(): string {
  if (typeof window === "undefined") return "anonymous";
  let id = localStorage.getItem("urr-anon-id");
  if (!id) {
    const fp = [
      screen.width,
      screen.height,
      screen.colorDepth,
      new Date().getTimezoneOffset(),
      navigator.language,
      navigator.hardwareConcurrency || 0,
    ].join("|");
    let hash = 0;
    for (let i = 0; i < fp.length; i++) {
      hash = (hash << 5) - hash + fp.charCodeAt(i);
      hash = hash & hash;
    }
    id = `anon-${Math.abs(hash).toString(36)}-${crypto.randomUUID().slice(0, 8)}`;
    localStorage.setItem("urr-anon-id", id);
  }
  return id;
}

export type VwrPhase = "checking" | "waiting" | "admitted" | "inactive" | "error";

interface VwrPollingState {
  phase: VwrPhase;
  position: number;
  servingCounter: number;
  ahead: number;
  totalInQueue: number;
  estimatedWait: number;
  errorMessage: string | null;
}

export function useVwrPolling(
  eventId: string,
  enabled: boolean,
  onAdmitted: () => void,
) {
  const [state, setState] = useState<VwrPollingState>({
    phase: "checking",
    position: 0,
    servingCounter: 0,
    ahead: 0,
    totalInQueue: 0,
    estimatedWait: 0,
    errorMessage: null,
  });

  const mountedRef = useRef(true);
  const pollTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const requestIdRef = useRef<string | null>(null);
  const admittedCalledRef = useRef(false);

  const cleanup = useCallback(() => {
    if (pollTimerRef.current) {
      clearTimeout(pollTimerRef.current);
      pollTimerRef.current = null;
    }
  }, []);

  useEffect(() => {
    mountedRef.current = true;
    admittedCalledRef.current = false;
    return () => {
      mountedRef.current = false;
      cleanup();
    };
  }, [cleanup]);

  useEffect(() => {
    if (!enabled || !eventId) return;

    const storageKey = `urr-vwr-${eventId}`;
    const userId = getAnonUserId();

    function setVwrCookie(token: string) {
      const isSecure = window.location.protocol === "https:";
      document.cookie = `urr-vwr-token=${token}; path=/; max-age=600; SameSite=Strict${isSecure ? "; Secure" : ""}`;
    }

    function handleAdmitted(token?: string) {
      if (admittedCalledRef.current) return;
      admittedCalledRef.current = true;
      if (token) setVwrCookie(token);
      localStorage.removeItem(storageKey);
      if (mountedRef.current) {
        setState((s) => ({ ...s, phase: "admitted" }));
      }
      onAdmitted();
    }

    async function pollCheck() {
      const reqId = requestIdRef.current;
      if (!reqId || !mountedRef.current) return;

      try {
        const data: VwrCheckResponse = await vwrApi.check(eventId, reqId, userId);

        if (!mountedRef.current) return;

        if (data.admitted && data.token) {
          handleAdmitted(data.token);
          return;
        }

        setState({
          phase: "waiting",
          position: data.position,
          servingCounter: data.servingCounter,
          ahead: data.ahead,
          totalInQueue: data.totalInQueue,
          estimatedWait: data.estimatedWait ?? 0,
          errorMessage: null,
        });

        const nextPoll = data.nextPoll ?? DEFAULT_POLL_SECONDS;
        pollTimerRef.current = setTimeout(pollCheck, nextPoll * 1000);
      } catch (err) {
        if (!mountedRef.current) return;
        // On 404, position expired — re-assign
        if (err instanceof Error && err.message.includes("404")) {
          localStorage.removeItem(storageKey);
          requestIdRef.current = null;
          startAssign();
          return;
        }
        pollTimerRef.current = setTimeout(pollCheck, DEFAULT_POLL_SECONDS * 1000);
      }
    }

    async function startAssign() {
      if (!mountedRef.current) return;

      try {
        const data = await vwrApi.assign(eventId, userId);
        if (!mountedRef.current) return;

        requestIdRef.current = data.requestId;
        localStorage.setItem(storageKey, JSON.stringify({
          requestId: data.requestId,
          position: data.position,
        }));

        setState({
          phase: "waiting",
          position: data.position,
          servingCounter: data.servingCounter,
          ahead: Math.max(0, data.position - data.servingCounter),
          totalInQueue: data.position,
          estimatedWait: data.estimatedWait,
          errorMessage: null,
        });

        pollTimerRef.current = setTimeout(pollCheck, DEFAULT_POLL_SECONDS * 1000);
      } catch (err) {
        if (!mountedRef.current) return;
        // 404 = VWR not active for this event → skip VWR
        if (err instanceof Error && err.message.includes("404")) {
          setState((s) => ({ ...s, phase: "inactive" }));
          handleAdmitted();
          return;
        }
        setState((s) => ({
          ...s,
          phase: "error",
          errorMessage: "대기열 접속에 실패했습니다.",
        }));
      }
    }

    async function init() {
      // Check VWR status first
      try {
        const status = await vwrApi.status(eventId);
        if (!mountedRef.current) return;

        if (!status.isActive) {
          setState((s) => ({ ...s, phase: "inactive" }));
          handleAdmitted();
          return;
        }
      } catch {
        // Status check failed — try assign anyway (assign will 404 if inactive)
      }

      // Check localStorage for existing session
      const saved = localStorage.getItem(storageKey);
      if (saved) {
        try {
          const { requestId, position } = JSON.parse(saved);
          if (requestId && position) {
            requestIdRef.current = requestId;
            setState((s) => ({ ...s, phase: "waiting", position }));
            pollCheck();
            return;
          }
        } catch { /* ignore corrupt data */ }
      }

      startAssign();
    }

    setState({ phase: "checking", position: 0, servingCounter: 0, ahead: 0, totalInQueue: 0, estimatedWait: 0, errorMessage: null });
    init();

    return cleanup;
  }, [enabled, eventId, onAdmitted, cleanup]);

  return state;
}
