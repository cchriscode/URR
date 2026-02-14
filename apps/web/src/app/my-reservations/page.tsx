"use client";

import Link from "next/link";
import { AuthGuard } from "@/components/auth-guard";
import { reservationsApi, transfersApi } from "@/lib/api-client";
import { useMyReservations } from "@/hooks/use-reservations";
import { useCountdown, formatCountdownShort } from "@/hooks/use-countdown";
import { formatEventDate } from "@/lib/format";

interface Reservation {
  id: string;
  reservation_number?: string;
  status?: string;
  event_title?: string;
  event_date?: string;
  total_amount?: number;
  created_at?: string;
  expires_at?: string;
}

function statusBadge(status?: string) {
  switch (status) {
    case "confirmed":
    case "completed":
      return { text: "확정", cls: "bg-sky-50 text-sky-600" };
    case "pending":
    case "waiting":
      return { text: "대기", cls: "bg-amber-50 text-amber-600" };
    case "cancelled":
      return { text: "취소", cls: "bg-red-50 text-red-500" };
    default:
      return { text: status ?? "대기", cls: "bg-slate-100 text-slate-500" };
  }
}

function ReservationRow({
  item,
  onCancel,
  onTransfer,
  onExpire,
}: {
  item: Reservation;
  onCancel: (id: string) => void;
  onTransfer: (id: string) => void;
  onExpire: () => void;
}) {
  const badge = statusBadge(item.status);
  const expiryTarget = item.status === "pending" ? item.expires_at : null;
  const timeLeft = useCountdown(expiryTarget, onExpire);
  const isUrgent = !timeLeft.isExpired && timeLeft.totalDays === 0 && timeLeft.hours === 0 && timeLeft.minutes < 1;

  return (
    <div className="rounded-xl border border-slate-200 bg-white p-4 transition-all hover:border-sky-300 hover:shadow-sm">
      <Link href={`/reservations/${item.id}`} className="block">
        <div className="flex items-center justify-between">
          <div>
            <p className="font-medium text-slate-900">
              {item.event_title ?? item.reservation_number ?? item.id.slice(0, 8)}
            </p>
            {item.event_date && (
              <p className="mt-0.5 text-xs text-slate-400">{formatEventDate(item.event_date)}</p>
            )}
          </div>
          <span className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${badge.cls}`}>
            {badge.text}
          </span>
        </div>
        {item.total_amount != null && (
          <p className="mt-2 text-sm text-slate-500">{item.total_amount.toLocaleString()}원</p>
        )}
      </Link>

      {/* Expiry timer for pending */}
      {item.status === "pending" && expiryTarget && !timeLeft.isExpired && (
        <div
          className={`mt-2 rounded-lg px-2.5 py-1.5 text-xs font-medium ${
            isUrgent ? "bg-red-50 text-red-600" : "bg-amber-50 text-amber-700"
          }`}
        >
          남은 시간: <span className="font-mono font-bold">{formatCountdownShort(timeLeft)}</span>
        </div>
      )}
      {item.status === "pending" && expiryTarget && timeLeft.isExpired && (
        <div className="mt-2 rounded-lg px-2.5 py-1.5 text-xs font-medium bg-red-50 text-red-500">
          만료됨
        </div>
      )}

      <div className="mt-3 flex gap-2">
        {item.status === "pending" && !timeLeft.isExpired && (
          <Link
            href={`/payment/${item.id}`}
            className="text-xs text-sky-500 hover:text-sky-600 font-medium"
          >
            결제하기
          </Link>
        )}
        {item.status === "confirmed" && (
          <button
            onClick={() => onTransfer(item.id)}
            className="text-xs text-amber-500 hover:text-amber-600 font-medium"
          >
            양도 등록
          </button>
        )}
        {(item.status === "pending" || item.status === "confirmed") && (
          <button
            onClick={() => onCancel(item.id)}
            className="text-xs text-red-400 hover:text-red-500"
          >
            취소하기
          </button>
        )}
      </div>
    </div>
  );
}

export default function MyReservationsPage() {
  const { data: items = [], isLoading, isError, refetch } = useMyReservations();

  const handleCancel = async (id: string) => {
    if (!confirm("예매를 취소하시겠습니까?")) return;
    try {
      await reservationsApi.cancel(id);
      refetch();
    } catch {
      alert("취소에 실패했습니다.");
    }
  };

  const handleTransfer = async (id: string) => {
    if (!confirm("이 티켓을 양도 마켓에 등록하시겠습니까?")) return;
    try {
      await transfersApi.create(id);
      alert("양도 등록이 완료되었습니다.");
      refetch();
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      alert(msg ?? "양도 등록에 실패했습니다. 해당 아티스트의 Silver 이상 멤버십이 필요합니다.");
    }
  };

  return (
    <AuthGuard>
      <div className="max-w-2xl mx-auto">
        <h1 className="text-2xl font-bold text-slate-900 mb-6">내 예매</h1>

        {isLoading ? (
          <div className="flex justify-center py-16">
            <div className="inline-block h-6 w-6 animate-spin rounded-full border-2 border-sky-500 border-t-transparent" />
          </div>
        ) : isError ? (
          <div className="rounded-2xl border border-red-200 bg-red-50 p-10 text-center">
            <p className="text-red-500 text-sm">예매 내역을 불러오지 못했습니다</p>
            <button
              onClick={() => refetch()}
              className="mt-3 inline-block text-sm text-sky-500 hover:text-sky-600"
            >
              다시 시도
            </button>
          </div>
        ) : items.length === 0 ? (
          <div className="rounded-2xl border border-slate-200 bg-white p-10 text-center">
            <p className="text-slate-400 text-sm">예매 내역이 없습니다</p>
            <Link href="/" className="mt-3 inline-block text-sm text-sky-500 hover:text-sky-600">
              이벤트 둘러보기 &rarr;
            </Link>
          </div>
        ) : (
          <div className="space-y-3">
            {items.map((item: Reservation) => (
              <ReservationRow
                key={item.id}
                item={item}
                onCancel={handleCancel}
                onTransfer={handleTransfer}
                onExpire={() => refetch()}
              />
            ))}
          </div>
        )}
      </div>
    </AuthGuard>
  );
}
