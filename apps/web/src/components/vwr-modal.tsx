"use client";

import { useVwrPolling } from "@/hooks/use-vwr-polling";

function formatWaitTime(seconds: number): string {
  if (seconds <= 0) return "곧 입장";
  if (seconds < 60) return `약 ${seconds}초`;
  const min = Math.floor(seconds / 60);
  const sec = seconds % 60;
  return sec > 0 ? `약 ${min}분 ${sec}초` : `약 ${min}분`;
}

interface VwrModalProps {
  eventId: string;
  open: boolean;
  onClose: () => void;
  onAdmitted: () => void;
}

export default function VwrModal({ eventId, open, onClose, onAdmitted }: VwrModalProps) {
  const { phase, position, servingCounter, ahead, estimatedWait, errorMessage } =
    useVwrPolling(eventId, open, onAdmitted);

  if (!open) return null;

  // VWR inactive or already admitted — don't render (onAdmitted handles navigation)
  if (phase === "inactive" || phase === "admitted") return null;

  const progressPct =
    servingCounter > 0 && position > 0
      ? Math.min(100, Math.floor((servingCounter / position) * 100))
      : 0;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm"
      onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}
    >
      <div className="w-full max-w-sm mx-4 rounded-2xl border border-slate-200 bg-white p-6 shadow-xl">
        {phase === "checking" && (
          <div className="flex flex-col items-center py-8">
            <div className="h-10 w-10 animate-spin rounded-full border-3 border-sky-500 border-t-transparent" />
            <p className="mt-4 text-sm text-slate-500">대기열 확인 중...</p>
          </div>
        )}

        {phase === "error" && (
          <div className="text-center py-6">
            <p className="text-sm text-red-600">{errorMessage ?? "오류가 발생했습니다."}</p>
            <button
              onClick={onClose}
              className="mt-4 rounded-lg border border-slate-200 px-4 py-2 text-sm text-slate-600 hover:bg-slate-50 transition-colors"
            >
              닫기
            </button>
          </div>
        )}

        {phase === "waiting" && (
          <>
            {/* Header */}
            <div className="text-center mb-6">
              <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-full bg-sky-50 border border-sky-100">
                <div className="h-7 w-7 animate-spin rounded-full border-2 border-sky-500 border-t-transparent" />
              </div>
              <h2 className="text-lg font-bold text-slate-900">접속 대기 중</h2>
              <p className="mt-1 text-xs text-slate-400">현재 접속자가 많아 순서대로 안내하고 있습니다</p>
            </div>

            {/* Position */}
            <div className="rounded-xl bg-sky-50 border border-sky-100 p-4 text-center mb-4">
              <p className="text-xs text-sky-600 mb-1">현재 대기 순번</p>
              <p className="text-3xl font-bold text-sky-700">
                {position.toLocaleString()}<span className="text-base font-medium">번</span>
              </p>
            </div>

            {/* Progress bar */}
            <div className="mb-3">
              <div className="h-1.5 w-full rounded-full bg-slate-100 overflow-hidden">
                <div
                  className="h-full rounded-full bg-gradient-to-r from-sky-400 to-blue-500 transition-all duration-500"
                  style={{ width: `${progressPct}%` }}
                />
              </div>
            </div>

            {/* Stats */}
            <div className="grid grid-cols-2 gap-3 mb-4">
              <div className="rounded-lg bg-slate-50 border border-slate-100 p-3 text-center">
                <p className="text-[10px] text-slate-400 mb-0.5">내 앞</p>
                <p className="text-sm font-bold text-slate-700">
                  {ahead.toLocaleString()}<span className="text-[10px] font-normal">명</span>
                </p>
              </div>
              <div className="rounded-lg bg-slate-50 border border-slate-100 p-3 text-center">
                <p className="text-[10px] text-slate-400 mb-0.5">예상 대기</p>
                <p className="text-sm font-bold text-slate-700">{formatWaitTime(estimatedWait)}</p>
              </div>
            </div>

            {/* Notice */}
            <div className="rounded-lg bg-amber-50 border border-amber-200 px-3 py-2 mb-4">
              <p className="text-xs text-amber-700 text-center leading-relaxed">
                이 창을 닫지 마세요. 순서가 되면 자동으로 예매 페이지로 이동합니다.
              </p>
            </div>

            {/* Close button */}
            <button
              onClick={onClose}
              className="block w-full rounded-lg border border-slate-200 bg-white px-4 py-2.5 text-center text-sm text-slate-500 hover:bg-slate-50 transition-colors"
            >
              대기열 나가기
            </button>
          </>
        )}
      </div>
    </div>
  );
}
