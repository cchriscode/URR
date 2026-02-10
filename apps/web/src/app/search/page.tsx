"use client";

import { useEffect, useState, useCallback, Suspense } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { eventsApi } from "@/lib/api-client";
import type { EventSummary } from "@/lib/types";

interface EventWithPrice extends EventSummary {
  min_price?: number;
  max_price?: number;
}

function SearchResults() {
  const searchParams = useSearchParams();
  const q = searchParams.get("q") ?? "";
  const [results, setResults] = useState<EventWithPrice[]>([]);
  const [loading, setLoading] = useState(false);

  const runSearch = useCallback(async (query: string) => {
    if (!query.trim()) {
      setResults([]);
      return;
    }
    setLoading(true);
    try {
      const { data } = await eventsApi.list({ q: query, page: 1, limit: 30 });
      setResults(data.events ?? data.data ?? []);
    } catch {
      setResults([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (q) runSearch(q);
  }, [q, runSearch]);

  return (
    <div className="max-w-2xl mx-auto">
      <h1 className="text-2xl font-bold text-slate-900 mb-2">검색 결과</h1>
      {q ? (
        <p className="text-sm text-slate-500 mb-6">
          &ldquo;{q}&rdquo; 검색 결과 {loading ? "..." : `${results.length}건`}
        </p>
      ) : (
        <p className="text-sm text-slate-500 mb-6">헤더에서 검색어를 입력하세요</p>
      )}

      {loading ? (
        <div className="flex justify-center py-16">
          <div className="inline-block h-6 w-6 animate-spin rounded-full border-2 border-sky-500 border-t-transparent" />
        </div>
      ) : results.length === 0 && q ? (
        <div className="rounded-xl bg-white border border-slate-200 p-10 text-center">
          <p className="text-slate-400">검색 결과가 없습니다</p>
        </div>
      ) : (
        <div className="space-y-2">
          {results.map((event) => (
            <Link
              key={event.id}
              href={`/events/${event.id}`}
              className="block rounded-xl border border-slate-200 bg-white p-4 transition-all hover:border-sky-300 hover:shadow-sm"
            >
              <p className="font-medium text-slate-900">{event.title}</p>
              <div className="mt-1.5 flex flex-wrap items-center gap-3 text-xs text-slate-500">
                {event.venue ? <span>&#128205; {event.venue}</span> : null}
                {event.event_date ? <span>&#128197; {event.event_date}</span> : null}
                {event.artist_name ? (
                  <span className="rounded-full bg-amber-50 px-2 py-0.5 text-amber-600">
                    {event.artist_name}
                  </span>
                ) : null}
                {event.min_price != null && event.max_price != null ? (
                  <span>&#128176; {event.min_price.toLocaleString()}원 ~ {event.max_price.toLocaleString()}원</span>
                ) : null}
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}

export default function SearchPage() {
  return (
    <Suspense fallback={
      <div className="flex justify-center py-16">
        <div className="inline-block h-6 w-6 animate-spin rounded-full border-2 border-sky-500 border-t-transparent" />
      </div>
    }>
      <SearchResults />
    </Suspense>
  );
}
