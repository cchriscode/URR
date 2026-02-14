export default function CommunityLoading() {
  return (
    <div className="max-w-3xl mx-auto animate-pulse">
      <div className="h-8 w-28 rounded bg-slate-100 mb-6" />
      <div className="space-y-3">
        {Array.from({ length: 5 }).map((_, i) => (
          <div key={i} className="rounded-xl border border-slate-200 bg-white p-4 space-y-2">
            <div className="h-5 w-2/3 rounded bg-slate-100" />
            <div className="h-4 w-1/3 rounded bg-slate-100" />
          </div>
        ))}
      </div>
    </div>
  );
}
