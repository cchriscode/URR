"use client";

import { useEffect, useState, Suspense } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { paymentsApi } from "@/lib/api-client";

function PaymentSuccessContent() {
  const searchParams = useSearchParams();
  const paymentKey = searchParams.get("paymentKey");
  const orderId = searchParams.get("orderId");
  const amount = searchParams.get("amount");
  const isTossCallback = !!(paymentKey && orderId && amount);

  const [confirming, setConfirming] = useState(isTossCallback);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!isTossCallback) return;
    paymentsApi
      .confirm({ paymentKey, orderId, amount: Number(amount) })
      .then(() => setConfirming(false))
      .catch((err: unknown) => {
        const resp = (err as { response?: { status?: number; data?: { message?: string } } }).response;
        // Already confirmed (e.g. page refresh) — treat as success
        if (resp?.status === 400 && resp?.data?.message?.includes("already confirmed")) {
          setConfirming(false);
          return;
        }
        setError("결제 확인에 실패했습니다. 고객센터에 문의해주세요.");
        setConfirming(false);
      });
  }, [isTossCallback, paymentKey, orderId, amount]);

  if (confirming) {
    return (
      <div className="flex min-h-[50vh] items-center justify-center">
        <div className="text-center">
          <div className="inline-block h-8 w-8 animate-spin rounded-full border-2 border-sky-500 border-t-transparent" />
          <p className="mt-4 text-sm text-slate-500">결제를 확인하는 중...</p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex min-h-[50vh] items-center justify-center">
        <div className="max-w-sm text-center">
          <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-red-50 text-3xl text-red-500">
            &#10007;
          </div>
          <h1 className="mt-4 text-xl font-bold text-slate-900">결제 확인 실패</h1>
          <p className="mt-2 text-sm text-slate-500">{error}</p>
          <div className="mt-6 flex flex-col gap-2">
            <Link
              href="/my-reservations"
              className="rounded-lg bg-sky-500 px-5 py-2.5 text-sm font-medium text-white hover:bg-sky-600 transition-colors"
            >
              내 예매 확인
            </Link>
            <Link
              href="/"
              className="rounded-lg border border-slate-200 px-5 py-2.5 text-sm font-medium text-slate-600 hover:border-sky-300 transition-colors"
            >
              홈으로 돌아가기
            </Link>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="flex min-h-[50vh] items-center justify-center">
      <div className="max-w-sm text-center">
        <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-sky-50 text-3xl text-sky-600">
          &#10003;
        </div>
        <h1 className="mt-4 text-xl font-bold text-slate-900">결제 완료</h1>
        <p className="mt-2 text-sm text-slate-500">
          결제가 성공적으로 처리되었습니다. 예매가 확정되었습니다.
        </p>
        <div className="mt-6 flex flex-col gap-2">
          <Link
            href="/my-reservations"
            className="rounded-lg bg-sky-500 px-5 py-2.5 text-sm font-medium text-white hover:bg-sky-600 transition-colors"
          >
            내 예매 확인
          </Link>
          <Link
            href="/"
            className="rounded-lg border border-slate-200 px-5 py-2.5 text-sm font-medium text-slate-600 hover:border-sky-300 transition-colors"
          >
            홈으로 돌아가기
          </Link>
        </div>
      </div>
    </div>
  );
}

export default function PaymentCallbackSuccessPage() {
  return (
    <Suspense
      fallback={
        <div className="flex min-h-[50vh] items-center justify-center">
          <div className="inline-block h-6 w-6 animate-spin rounded-full border-2 border-sky-500 border-t-transparent" />
        </div>
      }
    >
      <PaymentSuccessContent />
    </Suspense>
  );
}
