export default function EventDetailLoading() {
  return (
    <div className="max-w-2xl mx-auto space-y-6 animate-pulse">
      <div className="rounded-2xl bg-slate-100 h-64" />
      <div className="rounded-2xl border border-slate-200 bg-white p-6 space-y-4">
        <div className="h-7 w-2/3 rounded bg-slate-100" />
        <div className="h-4 w-1/3 rounded bg-slate-100" />
        <div className="space-y-3 mt-4">
          <div className="flex items-center gap-3">
            <div className="h-8 w-8 rounded-lg bg-slate-100" />
            <div className="h-4 w-40 rounded bg-slate-100" />
          </div>
          <div className="flex items-center gap-3">
            <div className="h-8 w-8 rounded-lg bg-slate-100" />
            <div className="h-4 w-48 rounded bg-slate-100" />
          </div>
        </div>
      </div>
      <div className="rounded-2xl border border-slate-200 bg-white p-6 space-y-3">
        <div className="h-5 w-24 rounded bg-slate-100" />
        <div className="h-14 rounded-lg bg-slate-50" />
        <div className="h-14 rounded-lg bg-slate-50" />
      </div>
    </div>
  );
}
