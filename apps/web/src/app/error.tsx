"use client";

export default function Error({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <div className="min-h-[60vh] flex items-center justify-center">
      <div className="text-center p-8">
        <div className="text-4xl mb-4">&#9888;&#65039;</div>
        <h2 className="text-2xl font-bold text-slate-900 mb-2">
          문제가 발생했습니다
        </h2>
        <p className="text-slate-500 mb-6 text-sm">
          예상치 못한 오류가 발생했습니다. 다시 시도해주세요.
        </p>
        {error?.digest && (
          <p className="text-xs text-slate-400 mb-4">
            오류 ID: {error.digest}
          </p>
        )}
        <button
          onClick={reset}
          className="px-6 py-2.5 bg-sky-500 text-white rounded-lg font-medium hover:bg-sky-600 transition-colors"
        >
          다시 시도
        </button>
      </div>
    </div>
  );
}
