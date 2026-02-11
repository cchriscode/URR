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

  // undefined = still loading event; null = standing (no seat map); string = has seat map
  // Derived from server data — not from URL params — to prevent client-side bypass.
  const [seatLayoutId, setSeatLayoutId] = useState<string | null | undefined>(undefined);

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

  // Join queue on mount
  useEffect(() => {
    if (!eventId) return;
    queueApi.check(eventId).then(() => setJoined(true)).catch(() => setJoined(true));
  }, [eventId]);

  const { status } = useQueuePolling(eventId, joined);

  // Auto-redirect when admitted — wait for event data before deciding destination
  useEffect(() => {
    if (!status) return;
    if (seatLayoutId === undefined) return; // event detail still loading
    if (status.status === "active" || (!status.queued && status.status !== "queued")) {
      if (seatLayoutId) {
        router.replace(`/events/${eventId}/seats`);
      } else {
        router.replace(`/events/${eventId}/book`);
      }
    }
  }, [status, eventId, router, seatLayoutId]);

  const hasSeats = Boolean(seatLayoutId);

  const position = status?.position ?? 0;
  const ahead = status?.peopleAhead ?? (position > 0 ? position - 1 : 0);
  const behind = status?.peopleBehind ?? 0;
  const waitTime = formatWaitTime(status?.estimatedWait);
  const eventTitle = status?.eventInfo?.title;
  const eventArtist = status?.eventInfo?.artist;
  const isQueued = status?.queued || status?.status === "queued";

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
              <div className="mt-6 space-y-4">
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
                {status?.currentUsers != null && status?.threshold != null && (
                  <div className="text-xs text-slate-400">
                    현재 {status.currentUsers.toLocaleString()}명 접속 중 (최대 {status.threshold.toLocaleString()}명)
                  </div>
                )}
              </div>
            ) : !status ? (
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
