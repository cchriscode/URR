"use client";

import Link from "next/link";

export default function CommunityError({
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <div className="min-h-[40vh] flex items-center justify-center">
      <div className="text-center p-8">
        <h2 className="text-xl font-bold text-slate-900 mb-2">
          커뮤니티를 불러올 수 없습니다
        </h2>
        <p className="text-slate-500 mb-6 text-sm">
          잠시 후 다시 시도해주세요.
        </p>
        <div className="flex gap-3 justify-center">
          <button
            onClick={reset}
            className="px-5 py-2 bg-sky-500 text-white rounded-lg text-sm font-medium hover:bg-sky-600 transition-colors"
          >
            다시 시도
          </button>
          <Link
            href="/"
            className="px-5 py-2 border border-slate-200 text-slate-600 rounded-lg text-sm font-medium hover:border-sky-300 transition-colors"
          >
            홈으로
          </Link>
        </div>
      </div>
    </div>
  );
}
