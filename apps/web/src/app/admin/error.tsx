"use client";

export default function AdminError({
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <div className="min-h-[40vh] flex items-center justify-center">
      <div className="text-center p-8">
        <h2 className="text-xl font-bold text-slate-900 mb-2">
          관리자 페이지 오류
        </h2>
        <p className="text-slate-500 mb-6 text-sm">
          데이터를 불러오는 중 문제가 발생했습니다.
        </p>
        <button
          onClick={reset}
          className="px-5 py-2 bg-sky-500 text-white rounded-lg text-sm font-medium hover:bg-sky-600 transition-colors"
        >
          다시 시도
        </button>
      </div>
    </div>
  );
}
