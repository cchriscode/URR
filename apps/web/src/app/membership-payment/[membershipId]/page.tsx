"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter, useSearchParams } from "next/navigation";
import { AuthGuard } from "@/components/auth-guard";
import { membershipsApi, paymentsApi } from "@/lib/api-client";

const METHODS = [
  { id: "naver_pay", label: "네이버페이", icon: "N", iconCls: "bg-green-500 text-white" },
  { id: "kakao_pay", label: "카카오페이", icon: "K", iconCls: "bg-yellow-400 text-slate-900" },
  { id: "bank_transfer", label: "계좌이체", icon: "\uD83C\uDFE6", iconCls: "bg-slate-600 text-white" },
  { id: "toss", label: "토스페이먼츠", icon: "T", iconCls: "bg-blue-500 text-white" },
] as const;

type MethodId = (typeof METHODS)[number]["id"];

const tierBenefits = [
  { label: "등급", value: "Silver (Lv.2)" },
  { label: "선예매", value: "선예매 3 접근" },
  { label: "양도", value: "양도 기능 이용 가능" },
  { label: "포인트", value: "가입 보너스 200pt" },
];

export default function MembershipPaymentPage() {
  const params = useParams<{ membershipId: string }>();
  const searchParams = useSearchParams();
  const router = useRouter();
  const [method, setMethod] = useState<MethodId | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [priceLoading, setPriceLoading] = useState(true);

  const artistName = searchParams.get("artistName") ?? "아티스트";
  const artistId = searchParams.get("artistId") ?? "";

  // Fetch the actual price from the backend instead of trusting URL params.
  // This prevents price manipulation via URL query string editing.
  const [price, setPrice] = useState<number>(0);

  useEffect(() => {
    if (!artistId) {
      // Fallback: if no artistId, use URL param but backend MUST validate during payment
      const urlPrice = Number(searchParams.get("price") ?? "30000");
      setPrice(urlPrice);
      setPriceLoading(false);
      return;
    }

    membershipsApi
      .benefits(artistId)
      .then((res) => {
        const data = res.data;
        const serverPrice =
          data?.membership_price ?? data?.membershipPrice ?? data?.price;
        if (serverPrice != null && serverPrice > 0) {
          setPrice(Number(serverPrice));
        } else {
          // Fallback to URL param if benefits endpoint does not include price
          setPrice(Number(searchParams.get("price") ?? "30000"));
        }
      })
      .catch(() => {
        // Fallback to URL param on error; backend validates during payment processing
        setPrice(Number(searchParams.get("price") ?? "30000"));
      })
      .finally(() => setPriceLoading(false));
  }, [artistId, searchParams]);

  const handlePay = async () => {
    if (!method || !params.membershipId) return;
    setBusy(true);
    setError(null);

    try {
      if (method === "toss") {
        const prepRes = await paymentsApi.prepare({
          amount: price,
          paymentType: "membership",
          referenceId: params.membershipId,
        });
        const orderId = prepRes.data?.orderId ?? prepRes.data?.order_id;
        const clientKey = prepRes.data?.clientKey ?? prepRes.data?.client_key;

        const { loadTossPayments } = await import("@tosspayments/payment-sdk");
        const tossPayments = await loadTossPayments(clientKey);
        await tossPayments.requestPayment("카드", {
          amount: price,
          orderId,
          orderName: `${artistName} 멤버십`,
          successUrl: `${window.location.origin}/payment/success`,
          failUrl: `${window.location.origin}/payment/fail`,
        });
      } else {
        await paymentsApi.process({
          paymentMethod: method,
          paymentType: "membership",
          referenceId: params.membershipId,
        });
        // Redirect to artist page to see activated membership
        if (artistId) {
          router.push(`/artists/${artistId}`);
        } else {
          router.push("/my-memberships");
        }
      }
    } catch {
      setError("결제에 실패했습니다. 다시 시도해주세요.");
      setBusy(false);
    }
  };

  if (priceLoading) {
    return (
      <AuthGuard>
        <div className="flex items-center justify-center py-20">
          <div className="text-center">
            <div className="inline-block h-6 w-6 animate-spin rounded-full border-2 border-sky-500 border-t-transparent" />
            <p className="mt-3 text-sm text-slate-500">결제 정보 확인 중...</p>
          </div>
        </div>
      </AuthGuard>
    );
  }

  return (
    <AuthGuard>
      <div className="max-w-md mx-auto space-y-4">
        {/* Membership Info */}
        <div className="rounded-2xl border border-slate-200 bg-white p-6">
          <h1 className="text-xl font-bold text-slate-900 mb-4">멤버십 결제</h1>

          <div className="space-y-2 text-sm">
            <div className="flex justify-between">
              <span className="text-slate-400">아티스트</span>
              <span className="text-slate-700 font-medium">{artistName}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-slate-400">구독 기간</span>
              <span className="text-slate-700">1년</span>
            </div>
            <div className="flex justify-between border-t border-slate-100 pt-2 mt-2">
              <span className="text-slate-700 font-medium">결제 금액</span>
              <span className="font-bold text-sky-600 text-lg">{price.toLocaleString()}원</span>
            </div>
          </div>

          {/* Benefits summary */}
          <div className="mt-4 rounded-lg bg-sky-50 border border-sky-100 p-3">
            <p className="text-xs font-medium text-sky-700 mb-2">가입 시 혜택</p>
            <div className="space-y-1">
              {tierBenefits.map((b) => (
                <div key={b.label} className="flex justify-between text-xs">
                  <span className="text-slate-500">{b.label}</span>
                  <span className="text-sky-600">{b.value}</span>
                </div>
              ))}
            </div>
          </div>
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
          disabled={!method || busy}
          className="w-full rounded-xl bg-sky-500 px-6 py-3.5 font-medium text-white hover:bg-sky-600 disabled:opacity-50 transition-colors"
        >
          {busy ? (
            <span className="flex items-center justify-center gap-2">
              <span className="inline-block h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
              처리 중...
            </span>
          ) : (
            `${price.toLocaleString()}원 결제하기`
          )}
        </button>

        {error && (
          <p className="rounded-lg bg-red-50 border border-red-200 px-3 py-2 text-sm text-red-600 text-center">{error}</p>
        )}
      </div>
    </AuthGuard>
  );
}
