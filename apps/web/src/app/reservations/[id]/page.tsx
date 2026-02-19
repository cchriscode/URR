"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { AuthGuard } from "@/components/auth-guard";
import { reservationsApi } from "@/lib/api-client";
import { statusBadge } from "@/lib/status-badge";

interface SeatDisplay {
  label: string;
  price?: number;
}

interface ReservationDetail {
  id: string;
  reservation_number?: string;
  status?: string;
  event_title?: string;
  event_date?: string;
  venue?: string;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  items?: any;
  total_amount?: number;
  created_at?: string;
  expires_at?: string;
}

export default function ReservationDetailPage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const [reservation, setReservation] = useState<ReservationDetail | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!params.id) return;
    reservationsApi
      .byId(params.id)
      .then((res) => setReservation(res.data.reservation ?? res.data.data ?? res.data ?? null))
      .finally(() => setLoading(false));
  }, [params.id]);

  const handleCancel = async () => {
    if (!params.id || !confirm("예매를 취소하시겠습니까?")) return;
    try {
      await reservationsApi.cancel(params.id);
      router.push("/my-reservations");
    } catch {
      alert("취소에 실패했습니다.");
    }
  };

  if (loading) {
    return (
      <div className="flex justify-center py-20">
        <div className="inline-block h-6 w-6 animate-spin rounded-full border-2 border-sky-500 border-t-transparent" />
      </div>
    );
  }

  const badge = statusBadge(reservation?.status);

  // Parse items — backend returns json_agg as either parsed array or {type:"json", value:"[...]"}
  const seatList: SeatDisplay[] = (() => {
    const raw = reservation?.items;
    if (!raw) return [];
    let arr: Array<Record<string, unknown>> = [];
    if (Array.isArray(raw)) {
      arr = raw;
    } else if (typeof raw === "string") {
      try { arr = JSON.parse(raw); } catch { return []; }
    } else if (raw?.value && typeof raw.value === "string") {
      try { arr = JSON.parse(raw.value); } catch { return []; }
    }
    return arr.map((item) => ({
      label: String(item.seatLabel ?? item.seat_label ?? item.ticketTypeName ?? ""),
      price: Number(item.unitPrice ?? item.unit_price ?? item.subtotal ?? 0) || undefined,
    }));
  })();

  return (
    <AuthGuard>
      <div className="max-w-lg mx-auto space-y-4">
        <div className="rounded-2xl border border-slate-200 bg-white p-6">
          <div className="flex items-center justify-between mb-4">
            <h1 className="text-xl font-bold text-slate-900">예매 상세</h1>
            {reservation?.status && (
              <span className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${badge.cls}`}>
                {badge.text}
              </span>
            )}
          </div>

          <div className="space-y-3 text-sm">
            {reservation?.reservation_number && (
              <div className="flex justify-between">
                <span className="text-slate-400">예매번호</span>
                <span className="font-mono text-slate-700">{reservation.reservation_number}</span>
              </div>
            )}
            {reservation?.event_title && (
              <div className="flex justify-between">
                <span className="text-slate-400">이벤트</span>
                <span className="text-slate-700">{reservation.event_title}</span>
              </div>
            )}
            {reservation?.venue && (
              <div className="flex justify-between">
                <span className="text-slate-400">장소</span>
                <span className="text-slate-700">{reservation.venue}</span>
              </div>
            )}
            {reservation?.event_date && (
              <div className="flex justify-between">
                <span className="text-slate-400">공연일</span>
                <span className="text-slate-700">{new Date(reservation.event_date).toLocaleDateString("ko-KR")}</span>
              </div>
            )}
            {reservation?.created_at && (
              <div className="flex justify-between">
                <span className="text-slate-400">예매일</span>
                <span className="text-slate-700">{new Date(reservation.created_at).toLocaleDateString("ko-KR")}</span>
              </div>
            )}
            {reservation?.expires_at && reservation.status === "pending" && (
              <div className="flex justify-between">
                <span className="text-slate-400">만료</span>
                <span className="text-amber-600 font-medium">
                  {new Date(reservation.expires_at).toLocaleTimeString("ko-KR")}까지
                </span>
              </div>
            )}
          </div>

          {seatList.length > 0 && (
            <div className="mt-4 border-t border-slate-100 pt-4">
              <p className="text-xs font-medium text-slate-400 uppercase tracking-wider mb-2">좌석</p>
              <div className="flex flex-wrap gap-2">
                {seatList.map((seat, i) => (
                  <span key={i} className="rounded-lg bg-sky-50 border border-sky-100 px-2.5 py-1 text-xs text-sky-700">
                    {seat.label || `좌석 ${i + 1}`}
                    {seat.price ? ` (${seat.price.toLocaleString()}원)` : ""}
                  </span>
                ))}
              </div>
            </div>
          )}

          {reservation?.total_amount != null && (
            <div className="mt-4 border-t border-slate-100 pt-4 flex justify-between">
              <span className="font-medium text-slate-700">합계</span>
              <span className="font-bold text-slate-900">{reservation.total_amount.toLocaleString()}원</span>
            </div>
          )}
        </div>

        {/* Action buttons */}
        <div className="space-y-2">
          {reservation?.id && reservation.status === "pending" && (
            <Link
              href={`/payment/${reservation.id}`}
              className="block w-full rounded-xl bg-sky-500 px-6 py-3 text-center font-medium text-white hover:bg-sky-600 transition-colors"
            >
              결제하기
            </Link>
          )}
          {reservation?.status && (reservation.status === "pending" || reservation.status === "confirmed") && (
            <button
              onClick={handleCancel}
              className="block w-full rounded-xl border border-red-200 px-6 py-3 text-center text-sm font-medium text-red-500 hover:bg-red-50 transition-colors"
            >
              예매 취소
            </button>
          )}
        </div>

        <Link href="/my-reservations" className="block text-center text-sm text-slate-500 hover:text-sky-500">
          &larr; 내 예매로 돌아가기
        </Link>
      </div>
    </AuthGuard>
  );
}
