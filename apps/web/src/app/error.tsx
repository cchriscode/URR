"use client";

export default function Error({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <div className="min-h-screen flex items-center justify-center">
      <div className="text-center p-8">
        <h2 className="text-2xl font-bold text-slate-900 mb-4">
          Something went wrong
        </h2>
        <p className="text-slate-500 mb-6">
          An unexpected error occurred. Please try again.
        </p>
        {error?.digest && (
          <p className="text-xs text-slate-400 mb-4">
            Error ID: {error.digest}
          </p>
        )}
        <button
          onClick={reset}
          className="px-6 py-2.5 bg-sky-500 text-white rounded-lg font-medium hover:bg-sky-600 transition-colors"
        >
          Try again
        </button>
      </div>
    </div>
  );
}
