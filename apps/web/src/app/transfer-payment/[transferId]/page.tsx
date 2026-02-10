"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { AuthGuard } from "@/components/auth-guard";
import { transfersApi, paymentsApi } from "@/lib/api-client";
import { formatEventDate } from "@/lib/format";

interface TransferInfo {
  id: string;
  event_title?: string;
  event_date?: string;
  venue?: string;
  seats?: string;
  artist_name?: string;
  original_price: number;
  transfer_fee: number;
  transfer_fee_percent: number;
  total_price: number;
  status: string;
}

const METHODS = [
  { id: "naver_pay", label: "네이버페이", icon: "N", iconCls: "bg-green-500 text-white" },
  { id: "kakao_pay", label: "카카오페이", icon: "K", iconCls: "bg-yellow-400 text-slate-900" },
  { id: "bank_transfer", label: "계좌이체", icon: "\uD83C\uDFE6", iconCls: "bg-slate-600 text-white" },
  { id: "toss", label: "토스페이먼츠", icon: "T", iconCls: "bg-blue-500 text-white" },
] as const;

type MethodId = (typeof METHODS)[number]["id"];

export default function TransferPaymentPage() {
  const params = useParams<{ transferId: string }>();
  const router = useRouter();
  const [info, setInfo] = useState<TransferInfo | null>(null);
  const [method, setMethod] = useState<MethodId | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loadingInfo, setLoadingInfo] = useState(true);

  useEffect(() => {
    if (!params.transferId) return;
    transfersApi
      .detail(params.transferId)
      .then((res) => {
        const data = res.data.transfer ?? res.data;
        setInfo(data);
      })
      .catch(() => {})
      .finally(() => setLoadingInfo(false));
  }, [params.transferId]);

  const handlePay = async () => {
    if (!method || !params.transferId || !info) return;
    setBusy(true);
    setError(null);

    try {
      if (method === "toss") {
        const prepRes = await paymentsApi.prepare({
          amount: info.total_price,
          paymentType: "transfer",
          referenceId: params.transferId,
        });
        const orderId = prepRes.data?.orderId ?? prepRes.data?.order_id;
        const clientKey = prepRes.data?.clientKey ?? prepRes.data?.client_key;

        const { loadTossPayments } = await import("@tosspayments/payment-sdk");
        const tossPayments = await loadTossPayments(clientKey);
        await tossPayments.requestPayment("카드", {
          amount: info.total_price,
          orderId,
          orderName: `양도 - ${info.event_title ?? "티켓"}`,
          successUrl: `${window.location.origin}/payment/success`,
          failUrl: `${window.location.origin}/payment/fail`,
        });
      } else {
        await paymentsApi.process({
          paymentMethod: method,
          paymentType: "transfer",
          referenceId: params.transferId,
        });
        router.push("/payment/success");
      }
    } catch {
      setError("결제에 실패했습니다. 다시 시도해주세요.");
      setBusy(false);
    }
  };

  return (
    <AuthGuard>
      <div className="max-w-md mx-auto space-y-4">
        {/* Transfer Info */}
        <div className="rounded-2xl border border-slate-200 bg-white p-6">
          <h1 className="text-xl font-bold text-slate-900 mb-4">양도 구매</h1>

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
              {info.artist_name && (
                <div className="flex justify-between">
                  <span className="text-slate-400">아티스트</span>
                  <span className="text-slate-700">{info.artist_name}</span>
                </div>
              )}
              {info.event_date && (
                <div className="flex justify-between">
                  <span className="text-slate-400">일시</span>
                  <span className="text-slate-700">{formatEventDate(info.event_date)}</span>
                </div>
              )}
              {info.seats && (
                <div className="flex justify-between">
                  <span className="text-slate-400">좌석</span>
                  <span className="text-slate-700">{info.seats}</span>
                </div>
              )}
              <div className="border-t border-slate-100 pt-2 mt-2 space-y-1">
                <div className="flex justify-between">
                  <span className="text-slate-400">티켓 원가</span>
                  <span className="text-slate-700">{info.original_price.toLocaleString()}원</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-slate-400">양도 수수료 ({info.transfer_fee_percent}%)</span>
                  <span className="text-amber-600">{info.transfer_fee.toLocaleString()}원</span>
                </div>
                <div className="flex justify-between border-t border-slate-100 pt-1">
                  <span className="text-slate-700 font-medium">총 결제 금액</span>
                  <span className="font-bold text-sky-600 text-lg">{info.total_price.toLocaleString()}원</span>
                </div>
              </div>
            </div>
          ) : (
            <p className="text-sm text-red-500">양도 정보를 불러올 수 없습니다.</p>
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
          disabled={!method || busy || info?.status !== "listed"}
          className="w-full rounded-xl bg-sky-500 px-6 py-3.5 font-medium text-white hover:bg-sky-600 disabled:opacity-50 transition-colors"
        >
          {busy ? (
            <span className="flex items-center justify-center gap-2">
              <span className="inline-block h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
              처리 중...
            </span>
          ) : info?.total_price != null ? (
            `${info.total_price.toLocaleString()}원 결제하기`
          ) : (
            "결제하기"
          )}
        </button>

        {error && (
          <p className="rounded-lg bg-red-50 border border-red-200 px-3 py-2 text-sm text-red-600 text-center">{error}</p>
        )}
      </div>
    </AuthGuard>
  );
}
