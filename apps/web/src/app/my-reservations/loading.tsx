export default function MyReservationsLoading() {
  return (
    <div className="max-w-2xl mx-auto animate-pulse">
      <div className="h-8 w-32 rounded bg-slate-100 mb-6" />
      <div className="space-y-3">
        {Array.from({ length: 4 }).map((_, i) => (
          <div key={i} className="rounded-xl border border-slate-200 bg-white p-4 space-y-3">
            <div className="flex justify-between">
              <div className="h-5 w-48 rounded bg-slate-100" />
              <div className="h-5 w-12 rounded-full bg-slate-100" />
            </div>
            <div className="h-4 w-24 rounded bg-slate-100" />
          </div>
        ))}
      </div>
    </div>
  );
}
