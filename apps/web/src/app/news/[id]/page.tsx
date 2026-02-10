"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { newsApi } from "@/lib/api-client";
import { getUser } from "@/lib/storage";

interface NewsDetail {
  id: string;
  title: string;
  content: string;
  author?: string;
  author_id?: string;
  views?: number;
  is_pinned?: boolean;
  created_at?: string;
}

export default function NewsDetailPage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const [article, setArticle] = useState<NewsDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [deleting, setDeleting] = useState(false);
  const user = getUser();

  useEffect(() => {
    if (!params.id) return;
    newsApi
      .byId(params.id)
      .then((res) => {
        const data = res.data.news ?? res.data.data;
        setArticle(data);
      })
      .catch(() => setArticle(null))
      .finally(() => setLoading(false));
  }, [params.id]);

  const userId = user?.id ?? user?.userId;
  const isOwner = !!(userId && article?.author_id && userId === article.author_id);
  const isAdmin = user?.role === "admin";
  const canEdit = isOwner;
  const canDelete = isOwner || isAdmin;

  const handleDelete = async () => {
    if (!params.id) return;
    if (!window.confirm("정말 삭제하시겠습니까?")) return;
    setDeleting(true);
    try {
      await newsApi.delete(params.id);
      router.push("/news");
    } catch {
      alert("삭제에 실패했습니다.");
      setDeleting(false);
    }
  };

  if (loading) {
    return (
      <div className="flex justify-center py-20">
        <div className="inline-block h-6 w-6 animate-spin rounded-full border-2 border-sky-500 border-t-transparent" />
      </div>
    );
  }

  if (!article) {
    return (
      <div className="max-w-2xl mx-auto text-center py-20">
        <p className="text-slate-400">글을 찾을 수 없습니다.</p>
        <Link href="/news" className="mt-4 inline-block text-sm text-sky-500 hover:text-sky-600">
          목록으로 돌아가기
        </Link>
      </div>
    );
  }

  return (
    <div className="max-w-2xl mx-auto">
      <div className="flex items-center justify-between mb-4">
        <Link href="/news" className="text-sm text-slate-500 hover:text-sky-500">
          &larr; 목록으로
        </Link>
        <div className="flex gap-2">
          {canEdit && (
            <Link
              href={`/news/${params.id}/edit`}
              className="rounded-lg border border-slate-200 px-3 py-1.5 text-xs font-medium text-slate-600 hover:border-sky-300 transition-colors"
            >
              수정
            </Link>
          )}
          {canDelete && (
            <button
              onClick={handleDelete}
              disabled={deleting}
              className="rounded-lg border border-red-200 px-3 py-1.5 text-xs font-medium text-red-500 hover:bg-red-50 disabled:opacity-50 transition-colors"
            >
              {deleting ? "삭제 중..." : "삭제"}
            </button>
          )}
        </div>
      </div>

      <article className="rounded-2xl border border-slate-200 bg-white p-6">
        <div className="flex items-start gap-2 mb-2">
          {article.is_pinned && (
            <span className="shrink-0 rounded-full bg-amber-50 border border-amber-200 px-2 py-0.5 text-xs font-medium text-amber-600">
              고정
            </span>
          )}
          <h1 className="text-2xl font-bold text-slate-900">{article.title}</h1>
        </div>
        <div className="flex items-center gap-3 text-xs text-slate-400 mb-6">
          {article.author && <span>{article.author}</span>}
          {article.created_at && <span>{new Date(article.created_at).toLocaleDateString("ko-KR")}</span>}
          {article.views != null && <span>조회 {article.views}</span>}
        </div>
        <div className="text-sm text-slate-700 leading-relaxed whitespace-pre-wrap border-t border-slate-100 pt-4">
          {article.content || "내용 없음"}
        </div>
      </article>
    </div>
  );
}
