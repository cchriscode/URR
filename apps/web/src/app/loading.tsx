export default function Loading() {
  return (
    <div className="space-y-6 animate-pulse">
      <div className="rounded-2xl bg-slate-100 h-40" />
      <div className="flex gap-2">
        {Array.from({ length: 5 }).map((_, i) => (
          <div key={i} className="h-9 w-20 rounded-lg bg-slate-100" />
        ))}
      </div>
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {Array.from({ length: 6 }).map((_, i) => (
          <div key={i} className="rounded-xl border border-slate-200 bg-white overflow-hidden">
            <div className="h-40 bg-slate-100" />
            <div className="p-4 space-y-3">
              <div className="h-5 w-3/4 rounded bg-slate-100" />
              <div className="h-4 w-1/2 rounded bg-slate-100" />
              <div className="h-4 w-2/3 rounded bg-slate-100" />
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
