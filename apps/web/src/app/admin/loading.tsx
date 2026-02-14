export default function AdminLoading() {
  return (
    <div className="space-y-6 animate-pulse">
      <div className="h-8 w-28 rounded bg-slate-100" />
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {Array.from({ length: 4 }).map((_, i) => (
          <div key={i} className="rounded-xl border border-slate-200 bg-white p-5">
            <div className="h-4 w-20 rounded bg-slate-100 mb-3" />
            <div className="h-8 w-16 rounded bg-slate-100" />
          </div>
        ))}
      </div>
      <div className="rounded-xl border border-slate-200 bg-white p-6">
        <div className="h-6 w-40 rounded bg-slate-100 mb-4" />
        <div className="h-64 rounded bg-slate-50" />
      </div>
    </div>
  );
}
