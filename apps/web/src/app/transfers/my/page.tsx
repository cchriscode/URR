"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { AuthGuard } from "@/components/auth-guard";
import { transfersApi } from "@/lib/api-client";
import { formatEventDate } from "@/lib/format";
import type { TicketTransfer } from "@/lib/types";

const statusBadge: Record<string, { text: string; cls: string }> = {
  listed: { text: "양도 중", cls: "bg-amber-50 text-amber-600" },
  completed: { text: "양도 완료", cls: "bg-sky-50 text-sky-600" },
  cancelled: { text: "취소됨", cls: "bg-red-50 text-red-500" },
};

export default function MyTransfersPage() {
  const [transfers, setTransfers] = useState<TicketTransfer[]>([]);
  const [loading, setLoading] = useState(true);

  const loadData = () => {
    setLoading(true);
    transfersApi
      .my()
      .then((res) => setTransfers(res.data.transfers ?? []))
      .catch(() => setTransfers([]))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    loadData();
  }, []);

  const handleCancel = async (id: string) => {
    if (!confirm("양도를 취소하시겠습니까?")) return;
    try {
      await transfersApi.cancel(id);
      loadData();
    } catch {
      alert("취소에 실패했습니다.");
    }
  };

  return (
    <AuthGuard>
      <div className="max-w-2xl mx-auto">
        <div className="flex items-center justify-between mb-6">
          <h1 className="text-2xl font-bold text-slate-900">내 양도 목록</h1>
          <Link href="/transfers" className="text-sm text-sky-500 hover:text-sky-600 font-medium">
            양도 마켓 &rarr;
          </Link>
        </div>

        {loading ? (
          <div className="flex justify-center py-16">
            <div className="inline-block h-6 w-6 animate-spin rounded-full border-2 border-sky-500 border-t-transparent" />
          </div>
        ) : transfers.length === 0 ? (
          <div className="rounded-2xl border border-slate-200 bg-white p-10 text-center">
            <p className="text-slate-400 text-sm">등록한 양도가 없습니다</p>
          </div>
        ) : (
          <div className="space-y-3">
            {transfers.map((t) => {
              const badge = statusBadge[t.status] ?? statusBadge.listed;
              return (
                <div
                  key={t.id}
                  className="rounded-xl border border-slate-200 bg-white p-4"
                >
                  <div className="flex items-center justify-between">
                    <div>
                      <p className="font-medium text-slate-900">{t.event_title}</p>
                      <div className="mt-1 flex items-center gap-2 text-xs text-slate-400">
                        {t.artist_name && <span>{t.artist_name}</span>}
                        {t.event_date && <span>{formatEventDate(t.event_date)}</span>}
                        {t.seats && <span>좌석: {t.seats}</span>}
                      </div>
                    </div>
                    <span className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${badge.cls}`}>
                      {badge.text}
                    </span>
                  </div>
                  <div className="mt-2 flex items-center justify-between">
                    <span className="text-sm text-slate-600">
                      {t.total_price.toLocaleString()}원
                      <span className="text-xs text-slate-400 ml-1">(수수료 {t.transfer_fee_percent}%)</span>
                    </span>
                    {t.status === "listed" && (
                      <button
                        onClick={() => handleCancel(t.id)}
                        className="text-xs text-red-400 hover:text-red-500"
                      >
                        양도 취소
                      </button>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </AuthGuard>
  );
}
