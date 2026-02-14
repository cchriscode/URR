"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { newsApi } from "@/lib/api-client";
import { useAuth } from "@/lib/auth-context";

interface NewsItem {
  id: string;
  title: string;
  content?: string;
  author?: string;
  views?: number;
  is_pinned?: boolean;
  created_at?: string;
}

export default function NewsPage() {
  const [items, setItems] = useState<NewsItem[]>([]);
  const [loading, setLoading] = useState(true);
  const { user } = useAuth();

  useEffect(() => {
    newsApi
      .list()
      .then((res) => setItems(res.data.news ?? res.data.data ?? []))
      .catch(() => setItems([]))
      .finally(() => setLoading(false));
  }, []);

  return (
    <div className="max-w-2xl mx-auto">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-slate-900">공지사항</h1>
        {user && (
          <Link
            href="/news/create"
            className="rounded-lg bg-sky-500 px-4 py-2 text-sm font-medium text-white hover:bg-sky-600 transition-colors"
          >
            글쓰기
          </Link>
        )}
      </div>

      {loading ? (
        <div className="flex justify-center py-16">
          <div className="inline-block h-6 w-6 animate-spin rounded-full border-2 border-sky-500 border-t-transparent" />
        </div>
      ) : items.length === 0 ? (
        <div className="rounded-2xl border border-slate-200 bg-white p-10 text-center">
          <p className="text-slate-400 text-sm">아직 공지사항이 없습니다</p>
        </div>
      ) : (
        <div className="space-y-3">
          {items.map((item) => (
            <Link
              key={item.id}
              href={`/news/${item.id}`}
              className="block rounded-xl border border-slate-200 bg-white p-5 transition-all hover:border-sky-300 hover:shadow-sm"
            >
              <div className="flex items-start gap-2">
                {item.is_pinned && (
                  <span className="shrink-0 rounded-full bg-amber-50 border border-amber-200 px-2 py-0.5 text-xs font-medium text-amber-600">
                    고정
                  </span>
                )}
                <div className="flex-1 min-w-0">
                  <p className="font-medium text-slate-900">{item.title}</p>
                  {item.content && (
                    <p className="mt-1.5 text-sm text-slate-500 line-clamp-2">{item.content}</p>
                  )}
                  <div className="mt-2 flex items-center gap-3 text-xs text-slate-400">
                    {item.author && <span>{item.author}</span>}
                    {item.created_at && <span>{new Date(item.created_at).toLocaleDateString("ko-KR")}</span>}
                    {item.views != null && <span>조회 {item.views}</span>}
                  </div>
                </div>
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
