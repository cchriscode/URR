"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { AuthGuard } from "@/components/auth-guard";
import { paymentsApi, reservationsApi } from "@/lib/api-client";
import { useCountdown, formatCountdownShort } from "@/hooks/use-countdown";

interface ReservationInfo {
  id: string;
  reservation_number?: string;
  event_title?: string;
  total_amount?: number;
  status?: string;
  expires_at?: string;
  venue?: string;
  event_date?: string;
  seats?: Array<{ seatLabel?: string; seat_label?: string; price?: number }>;
}

const METHODS = [
  { id: "naver_pay", label: "네이버페이", icon: "N", iconCls: "bg-green-500 text-white" },
  { id: "kakao_pay", label: "카카오페이", icon: "K", iconCls: "bg-yellow-400 text-slate-900" },
  { id: "bank_transfer", label: "계좌이체", icon: "\uD83C\uDFE6", iconCls: "bg-slate-600 text-white" },
  { id: "toss", label: "토스페이먼츠", icon: "T", iconCls: "bg-blue-500 text-white" },
] as const;

type MethodId = (typeof METHODS)[number]["id"];

export default function PaymentPage() {
  const params = useParams<{ reservationId: string }>();
  const router = useRouter();
  const [info, setInfo] = useState<ReservationInfo | null>(null);
  const [method, setMethod] = useState<MethodId | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loadingInfo, setLoadingInfo] = useState(true);

  useEffect(() => {
    if (!params.reservationId) return;
    reservationsApi
      .byId(params.reservationId)
      .then((res) => {
        const data = res.data.reservation ?? res.data.data ?? res.data;
        setInfo(data);
      })
      .catch(() => {})
      .finally(() => setLoadingInfo(false));
  }, [params.reservationId]);

  const expiryCountdown = useCountdown(info?.expires_at, () => {
    router.push("/my-reservations");
  });

  const handlePay = async () => {
    if (!method || !params.reservationId) return;
    setBusy(true);
    setError(null);

    try {
      if (method === "toss") {
        // TossPayments SDK flow
        const prepRes = await paymentsApi.prepare({
          reservationId: params.reservationId,
          amount: info?.total_amount,
        });
        const orderId = prepRes.data?.orderId ?? prepRes.data?.order_id;
        const clientKey = prepRes.data?.clientKey ?? prepRes.data?.client_key;

        const { loadTossPayments } = await import("@tosspayments/payment-sdk");
        const tossPayments = await loadTossPayments(clientKey);
        await tossPayments.requestPayment("카드", {
          amount: info?.total_amount ?? 0,
          orderId,
          orderName: info?.event_title ?? "티켓 결제",
          successUrl: `${window.location.origin}/payment/success`,
          failUrl: `${window.location.origin}/payment/fail`,
        });
      } else {
        // NaverPay, KakaoPay, BankTransfer — mock instant success
        await paymentsApi.process({
          reservationId: params.reservationId,
          paymentMethod: method,
        });
        router.push("/payment/success");
      }
    } catch {
      setError("결제에 실패했습니다. 다시 시도해주세요.");
      setBusy(false);
    }
  };

  const isUrgent =
    !expiryCountdown.isExpired &&
    expiryCountdown.totalDays === 0 &&
    expiryCountdown.hours === 0 &&
    expiryCountdown.minutes < 1;

  return (
    <AuthGuard>
      <div className="max-w-md mx-auto space-y-4">
        {/* Reservation Info */}
        <div className="rounded-2xl border border-slate-200 bg-white p-6">
          <h1 className="text-xl font-bold text-slate-900 mb-4">결제</h1>

          {loadingInfo ? (
            <div className="flex justify-center py-6">
              <div className="inline-block h-5 w-5 animate-spin rounded-full border-2 border-sky-500 border-t-transparent" />
            </div>
          ) : info ? (
            <div className="space-y-2 text-sm">
              {info.event_title && (
                <div className="flex justify-between">
                  <span className="text-slate-400">이벤트</span>
                  <span className="text-slate-700 font-medium">{info.event_title}</span>
                </div>
              )}
              {info.reservation_number && (
                <div className="flex justify-between">
                  <span className="text-slate-400">예매번호</span>
                  <span className="font-mono text-slate-600 text-xs">{info.reservation_number}</span>
                </div>
              )}
              {info.seats && info.seats.length > 0 && (
                <div className="flex justify-between">
                  <span className="text-slate-400">좌석</span>
                  <span className="text-slate-700 text-xs">
                    {info.seats.map((s) => s.seatLabel ?? s.seat_label).join(", ")}
                  </span>
                </div>
              )}
              {info.total_amount != null && (
                <div className="flex justify-between border-t border-slate-100 pt-2 mt-2">
                  <span className="text-slate-700 font-medium">결제 금액</span>
                  <span className="font-bold text-sky-600 text-lg">{info.total_amount.toLocaleString()}원</span>
                </div>
              )}
            </div>
          ) : (
            <p className="text-sm text-slate-500">
              예매번호: <span className="font-mono">{params.reservationId?.slice(0, 8)}</span>
            </p>
          )}

          {/* Expiry timer */}
          {info?.expires_at && !expiryCountdown.isExpired && (
            <div
              className={`mt-4 rounded-lg px-3 py-2.5 text-center ${
                isUrgent
                  ? "bg-red-50 border border-red-200 text-red-600 animate-pulse"
                  : "bg-amber-50 border border-amber-200 text-amber-700"
              }`}
            >
              <span className="block text-xs text-slate-400 mb-0.5">결제 남은 시간</span>
              <span className="text-lg font-bold font-mono">{formatCountdownShort(expiryCountdown)}</span>
            </div>
          )}
          {info?.expires_at && expiryCountdown.isExpired && (
            <div className="mt-4 rounded-lg px-3 py-2.5 text-center bg-red-50 border border-red-200 text-red-600">
              <span className="text-sm font-medium">예매 시간이 만료되었습니다</span>
            </div>
          )}
        </div>

        {/* Payment Method Selection */}
        <div className="rounded-2xl border border-slate-200 bg-white p-6">
          <h2 className="text-sm font-medium text-slate-700 mb-3">결제 수단 선택</h2>
          <div className="grid grid-cols-2 gap-3">
            {METHODS.map((m) => (
              <button
                key={m.id}
                onClick={() => setMethod(m.id)}
                className={`flex items-center gap-3 rounded-xl border-2 p-3.5 transition-all ${
                  method === m.id
                    ? "border-sky-500 bg-sky-50"
                    : "border-slate-200 bg-white hover:border-sky-300"
                }`}
              >
                <div className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-lg text-sm font-bold ${m.iconCls}`}>
                  {m.icon}
                </div>
                <span className="text-sm font-medium text-slate-700">{m.label}</span>
              </button>
            ))}
          </div>
        </div>

        {/* Pay Button */}
        <button
          onClick={handlePay}
          disabled={!method || busy || expiryCountdown.isExpired}
          className="w-full rounded-xl bg-sky-500 px-6 py-3.5 font-medium text-white hover:bg-sky-600 disabled:opacity-50 transition-colors"
        >
          {busy ? (
            <span className="flex items-center justify-center gap-2">
              <span className="inline-block h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
              처리 중...
            </span>
          ) : info?.total_amount != null ? (
            `${info.total_amount.toLocaleString()}원 결제하기`
          ) : (
            "결제하기"
          )}
        </button>

        {error && (
          <p className="rounded-lg bg-red-50 border border-red-200 px-3 py-2 text-sm text-red-600 text-center">{error}</p>
        )}

        <p className="text-center text-xs text-slate-400">
          시간 내에 결제하지 않으면 좌석이 자동으로 취소됩니다.
        </p>
      </div>
    </AuthGuard>
  );
}
