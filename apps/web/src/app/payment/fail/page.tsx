"use client";

import { Suspense } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";

function PaymentFailContent() {
  const searchParams = useSearchParams();
  const code = searchParams.get("code");
  const message = searchParams.get("message");

  return (
    <div className="flex min-h-[50vh] items-center justify-center">
      <div className="max-w-sm text-center">
        <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-red-50 text-3xl text-red-500">
          &#10007;
        </div>
        <h1 className="mt-4 text-xl font-bold text-slate-900">결제 실패</h1>
        <p className="mt-2 text-sm text-slate-500">
          {message ?? "결제 처리 중 문제가 발생했습니다. 다시 시도해주세요."}
        </p>
        {code && (
          <p className="mt-1 text-xs text-slate-400">오류 코드: {code}</p>
        )}
        <div className="mt-6 flex flex-col gap-2">
          <Link
            href="/my-reservations"
            className="rounded-lg bg-sky-500 px-5 py-2.5 text-sm font-medium text-white hover:bg-sky-600 transition-colors"
          >
            내 예매
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

export default function PaymentCallbackFailPage() {
  return (
    <Suspense
      fallback={
        <div className="flex min-h-[50vh] items-center justify-center">
          <div className="inline-block h-6 w-6 animate-spin rounded-full border-2 border-sky-500 border-t-transparent" />
        </div>
      }
    >
      <PaymentFailContent />
    </Suspense>
  );
}
