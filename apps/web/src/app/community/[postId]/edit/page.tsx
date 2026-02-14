"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { communityApi } from "@/lib/api-client";
import { useAuth } from "@/lib/auth-context";
import { AuthGuard } from "@/components/auth-guard";
import type { CommunityPost } from "@/lib/types";

function EditForm() {
  const params = useParams();
  const router = useRouter();
  const postId = params.postId as string;
  const { user } = useAuth();
  const userId = user?.id ?? user?.userId;

  const [post, setPost] = useState<CommunityPost | null>(null);
  const [title, setTitle] = useState("");
  const [content, setContent] = useState("");
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    communityApi
      .postDetail(postId)
      .then((res) => {
        const p = res.data.post;
        setPost(p);
        setTitle(p.title);
        setContent(p.content);
        // Only author can edit
        if (userId && p.author_id !== userId) {
          router.replace(`/community/${postId}`);
        }
      })
      .catch(() => router.replace("/community"))
      .finally(() => setLoading(false));
  }, [postId, userId, router]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (submitting) return;
    setSubmitting(true);
    setError(null);
    try {
      await communityApi.updatePost(postId, {
        title: title.trim(),
        content: content.trim(),
      });
      router.push(`/community/${postId}`);
    } catch {
      setError("수정에 실패했습니다.");
      setSubmitting(false);
    }
  };

  if (loading) {
    return (
      <div className="flex justify-center py-20">
        <div className="inline-block h-6 w-6 animate-spin rounded-full border-2 border-sky-500 border-t-transparent" />
      </div>
    );
  }

  if (!post) return null;

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      <h1 className="text-2xl font-bold text-slate-900">글 수정</h1>

      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label className="block text-sm font-medium text-slate-700 mb-1">제목</label>
          <input
            type="text"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            maxLength={255}
            required
            className="w-full rounded-lg border border-slate-200 px-3 py-2.5 text-sm focus:border-sky-400 focus:outline-none focus:ring-1 focus:ring-sky-400"
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-slate-700 mb-1">내용</label>
          <textarea
            value={content}
            onChange={(e) => setContent(e.target.value)}
            required
            rows={12}
            className="w-full rounded-lg border border-slate-200 px-3 py-2.5 text-sm focus:border-sky-400 focus:outline-none focus:ring-1 focus:ring-sky-400"
            style={{ minHeight: 200 }}
          />
        </div>

        {error && (
          <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-2 text-sm text-red-600">
            {error}
          </div>
        )}

        <div className="flex justify-end gap-2 pt-2">
          <button
            type="button"
            onClick={() => router.back()}
            className="rounded-lg border border-slate-200 px-4 py-2.5 text-sm text-slate-600 hover:border-sky-300"
          >
            취소
          </button>
          <button
            type="submit"
            disabled={submitting || !title.trim() || !content.trim()}
            className="rounded-lg bg-sky-500 px-6 py-2.5 text-sm font-medium text-white hover:bg-sky-600 disabled:opacity-40 transition-colors"
          >
            {submitting ? "수정 중..." : "수정"}
          </button>
        </div>
      </form>
    </div>
  );
}

export default function EditPage() {
  return (
    <AuthGuard>
      <EditForm />
    </AuthGuard>
  );
}
