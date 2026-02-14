"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { AuthGuard } from "@/components/auth-guard";
import { newsApi } from "@/lib/api-client";
import { useAuth } from "@/lib/auth-context";

export default function NewsEditPage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const [title, setTitle] = useState("");
  const [content, setContent] = useState("");
  const [isPinned, setIsPinned] = useState(false);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { user } = useAuth();

  useEffect(() => {
    if (!params.id) return;
    newsApi
      .byId(params.id)
      .then((res) => {
        const data = res.data.news ?? res.data.data;
        if (!data) {
          router.replace("/news");
          return;
        }
        const userId = user?.id ?? user?.userId;
        if (data.author_id && userId && data.author_id !== userId) {
          router.replace(`/news/${params.id}`);
          return;
        }
        setTitle(data.title ?? "");
        setContent(data.content ?? "");
        setIsPinned(data.is_pinned ?? false);
      })
      .catch(() => router.replace("/news"))
      .finally(() => setLoading(false));
  }, [params.id, router, user]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!params.id || !title.trim() || !content.trim()) {
      setError("제목과 내용을 모두 입력해주세요.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await newsApi.update(params.id, {
        title: title.trim(),
        content: content.trim(),
        is_pinned: user?.role === "admin" ? isPinned : undefined,
      });
      router.push(`/news/${params.id}`);
    } catch {
      setError("수정에 실패했습니다. 다시 시도해주세요.");
      setSubmitting(false);
    }
  };

  if (loading) {
    return (
      <AuthGuard>
        <div className="flex justify-center py-20">
          <div className="inline-block h-6 w-6 animate-spin rounded-full border-2 border-sky-500 border-t-transparent" />
        </div>
      </AuthGuard>
    );
  }

  return (
    <AuthGuard>
      <div className="max-w-2xl mx-auto">
        <h1 className="text-2xl font-bold text-slate-900 mb-6">글 수정</h1>
        <form onSubmit={handleSubmit} className="rounded-2xl border border-slate-200 bg-white p-6 space-y-4">
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1">제목</label>
            <input
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              className="w-full rounded-lg border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm text-slate-900 focus:border-sky-400 focus:outline-none focus:ring-1 focus:ring-sky-400"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1">내용</label>
            <textarea
              value={content}
              onChange={(e) => setContent(e.target.value)}
              rows={10}
              className="w-full rounded-lg border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm text-slate-900 focus:border-sky-400 focus:outline-none focus:ring-1 focus:ring-sky-400 resize-y min-h-[200px]"
            />
          </div>

          {user?.role === "admin" && (
            <label className="flex items-center gap-2 text-sm text-slate-700">
              <input
                type="checkbox"
                checked={isPinned}
                onChange={(e) => setIsPinned(e.target.checked)}
                className="rounded border-slate-300"
              />
              고정 글로 등록
            </label>
          )}

          {error && (
            <p className="rounded-lg bg-red-50 border border-red-200 px-3 py-2 text-sm text-red-600">{error}</p>
          )}

          <div className="flex gap-2">
            <button
              type="button"
              onClick={() => router.back()}
              className="rounded-lg border border-slate-200 px-4 py-2.5 text-sm font-medium text-slate-600 hover:bg-slate-50 transition-colors"
            >
              취소
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="flex-1 rounded-lg bg-sky-500 px-4 py-2.5 text-sm font-medium text-white hover:bg-sky-600 disabled:opacity-50 transition-colors"
            >
              {submitting ? "수정 중..." : "수정하기"}
            </button>
          </div>
        </form>
      </div>
    </AuthGuard>
  );
}
