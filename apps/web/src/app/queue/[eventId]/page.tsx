"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { AuthGuard } from "@/components/auth-guard";
import { queueApi, eventsApi } from "@/lib/api-client";
import { useQueuePolling } from "@/hooks/use-queue-polling";

function formatWaitTime(seconds?: number): string {
  if (seconds == null || seconds <= 0) return "-";
  if (seconds < 60) return `약 ${seconds}초`;
  const minutes = Math.ceil(seconds / 60);
  if (minutes < 60) return `약 ${minutes}분`;
  const hours = Math.floor(minutes / 60);
  const remainMin = minutes % 60;
  return remainMin > 0 ? `약 ${hours}시간 ${remainMin}분` : `약 ${hours}시간`;
}

export default function QueuePage() {
  const params = useParams<{ eventId: string }>();
  const router = useRouter();
  const eventId = params.eventId ?? "";
  const [joined, setJoined] = useState(false);
  const [vwrPosition, setVwrPosition] = useState<number | null>(null);

  // Extract VWR token info for priority bridging
  useEffect(() => {
    const vwrToken = document.cookie
      .split('; ')
      .find(row => row.startsWith('urr-vwr-token='))
      ?.split('=')[1];

    if (vwrToken) {
      try {
        const payload = JSON.parse(atob(vwrToken.split('.')[1]));
        setVwrPosition(payload.position || null);
      } catch {
        // Invalid token, ignore
      }
    }
  }, []);

  // undefined = still loading event; null = standing (no seat map); string = has seat map
  // Derived from server data — not from URL params — to prevent client-side bypass.
  const [seatLayoutId, setSeatLayoutId] = useState<string | null | undefined>(undefined);

  // Notify backend when user leaves the page
  useEffect(() => {
    const handleBeforeUnload = () => {
      navigator.sendBeacon(`${process.env.NEXT_PUBLIC_API_URL || ''}/api/v1/queue/leave/${eventId}`);
    };
    window.addEventListener('beforeunload', handleBeforeUnload);
    return () => window.removeEventListener('beforeunload', handleBeforeUnload);
  }, [eventId]);

  // Fetch event detail to determine seated vs standing flow
  useEffect(() => {
    if (!eventId) return;
    eventsApi
      .detail(eventId)
      .then(({ data }) => {
        const ev = data.event ?? data.data ?? data;
        const layoutId: string | null =
          ev?.seat_layout_id ?? ev?.seatLayoutId ?? null;
        setSeatLayoutId(layoutId);
      })
      .catch(() => {
        // On error, default null (standing) so user isn't stuck; backend still validates
        setSeatLayoutId(null);
      });
  }, [eventId]);

  // Track check() response for immediate redirect
  const [checkResult, setCheckResult] = useState<{ status?: string; queued?: boolean } | null>(null);

  // Join queue on mount
  useEffect(() => {
    if (!eventId) return;
    // Read VWR position directly from cookie to avoid state timing issues
    let vwrPos: number | null = null;
    try {
      const vwrToken = document.cookie
        .split('; ')
        .find(row => row.startsWith('urr-vwr-token='))
        ?.split('=')[1];
      if (vwrToken) {
        const payload = JSON.parse(atob(vwrToken.split('.')[1]));
        vwrPos = payload.position || null;
      }
    } catch {
      // Invalid token, ignore
    }
    queueApi.check(eventId, vwrPos).then(({ data }) => {
      // Store entry token as cookie for Lambda@Edge verification
      if (data?.entryToken) {
        const isSecure = window.location.protocol === 'https:';
        document.cookie = `urr-entry-token=${data.entryToken}; path=/; max-age=600; SameSite=Strict${isSecure ? '; Secure' : ''}`;
      }
      setCheckResult(data);
      // Only start polling if actually queued
      if (data?.queued || data?.status === "queued") {
        setJoined(true);
      }
    }).catch(() => {
      // Don't start polling on failure — show error state instead
      setCheckResult({ status: "error", queued: false });
    });
  }, [eventId]);

  // Only poll when actually queued (not when immediately admitted)
  const { status } = useQueuePolling(eventId, joined);

  // Merge check result and polling status for redirect decision
  const effectiveStatus = status ?? checkResult;

  // Auto-redirect when admitted — wait for event data before deciding destination
  useEffect(() => {
    if (!effectiveStatus) return;
    if (seatLayoutId === undefined) return; // event detail still loading
    if (effectiveStatus.status === "active" || (!effectiveStatus.queued && effectiveStatus.status !== "queued")) {
      if (seatLayoutId) {
        router.replace(`/events/${eventId}/seats`);
      } else {
        router.replace(`/events/${eventId}/book`);
      }
    }
  }, [effectiveStatus, eventId, router, seatLayoutId]);

  const hasSeats = Boolean(seatLayoutId);

  const displayStatus = status ?? checkResult;
  const position = (displayStatus as Record<string, unknown>)?.position as number ?? 0;
  const ahead = (displayStatus as Record<string, unknown>)?.peopleAhead as number ?? (position > 0 ? position - 1 : 0);
  const behind = (displayStatus as Record<string, unknown>)?.peopleBehind as number ?? 0;
  const waitTime = formatWaitTime((displayStatus as Record<string, unknown>)?.estimatedWait as number | undefined);
  const eventInfo = (displayStatus as Record<string, unknown>)?.eventInfo as Record<string, string> | undefined;
  const eventTitle = eventInfo?.title;
  const eventArtist = eventInfo?.artist;
  const isQueued = displayStatus?.queued || displayStatus?.status === "queued";

  return (
    <AuthGuard>
      <div className="flex min-h-[60vh] items-center justify-center">
        <div className="w-full max-w-md space-y-6">
          {/* Header card */}
          <div className="rounded-2xl border border-slate-200 bg-white p-8 text-center">
            {/* Animated waiting indicator */}
            <div className="mx-auto mb-6 flex h-20 w-20 items-center justify-center rounded-full bg-sky-50 border border-sky-100">
              <div className="h-10 w-10 animate-spin rounded-full border-3 border-sky-500 border-t-transparent" />
            </div>

            <h1 className="text-xl font-bold text-slate-900">대기열 안내</h1>
            {eventTitle && (
              <p className="mt-1 text-sm text-slate-500">
                {eventArtist && <span className="text-amber-600">{eventArtist}</span>}
                {eventArtist && " - "}
                {eventTitle}
              </p>
            )}

            {isQueued ? (
              <div className="mt-6 space-y-4" role="status" aria-live="polite" aria-label="대기열 상태">
                {/* Position */}
                <div className="rounded-xl bg-sky-50 border border-sky-100 p-4">
                  <p className="text-xs text-sky-600 mb-1">현재 대기 순번</p>
                  <p className="text-4xl font-bold text-sky-700">{position.toLocaleString()}<span className="text-lg font-medium">번</span></p>
                </div>

                {/* Stats row */}
                <div className="grid grid-cols-3 gap-3">
                  <div className="rounded-lg bg-slate-50 border border-slate-100 p-3 text-center">
                    <p className="text-[10px] text-slate-400 mb-0.5">내 앞</p>
                    <p className="text-sm font-bold text-slate-700">{ahead.toLocaleString()}<span className="text-[10px] font-normal">명</span></p>
                  </div>
                  <div className="rounded-lg bg-slate-50 border border-slate-100 p-3 text-center">
                    <p className="text-[10px] text-slate-400 mb-0.5">내 뒤</p>
                    <p className="text-sm font-bold text-slate-700">{behind.toLocaleString()}<span className="text-[10px] font-normal">명</span></p>
                  </div>
                  <div className="rounded-lg bg-slate-50 border border-slate-100 p-3 text-center">
                    <p className="text-[10px] text-slate-400 mb-0.5">예상 대기</p>
                    <p className="text-sm font-bold text-slate-700">{waitTime}</p>
                  </div>
                </div>

                {/* Progress info */}
                {(displayStatus as Record<string, unknown>)?.currentUsers != null && (displayStatus as Record<string, unknown>)?.threshold != null && (
                  <div className="text-xs text-slate-400">
                    현재 {((displayStatus as Record<string, unknown>).currentUsers as number).toLocaleString()}명 접속 중 (최대 {((displayStatus as Record<string, unknown>).threshold as number).toLocaleString()}명)
                  </div>
                )}
              </div>
            ) : !displayStatus ? (
              <p className="mt-6 text-sm text-slate-400">대기열 상태를 확인하고 있습니다...</p>
            ) : null}
          </div>

          {/* Notice */}
          <div className="rounded-xl bg-amber-50 border border-amber-200 px-4 py-3">
            <p className="text-xs text-amber-700 text-center leading-relaxed">
              이 페이지를 닫지 마세요. 순서가 되면 자동으로{" "}
              {hasSeats ? "좌석 선택 페이지" : "티켓 선택 페이지"}로 이동합니다.
            </p>
          </div>

          {/* Leave queue button */}
          <button
            onClick={() => {
              queueApi.leave(eventId).catch(() => {});
              router.push(`/events/${eventId}`);
            }}
            className="block w-full rounded-xl border border-slate-200 bg-white px-4 py-3 text-center text-sm text-slate-500 hover:bg-slate-50 transition-colors"
          >
            대기열 나가기
          </button>
        </div>
      </div>
    </AuthGuard>
  );
}
