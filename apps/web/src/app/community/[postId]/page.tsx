"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { communityApi, artistsApi } from "@/lib/api-client";
import { getUser } from "@/lib/storage";
import { formatDateTime } from "@/lib/format";
import type { CommunityPost, CommunityComment } from "@/lib/types";

export default function PostDetailPage() {
  const params = useParams();
  const router = useRouter();
  const postId = params.postId as string;
  const user = getUser();

  const [post, setPost] = useState<CommunityPost | null>(null);
  const [comments, setComments] = useState<CommunityComment[]>([]);
  const [artistName, setArtistName] = useState("");
  const [loading, setLoading] = useState(true);
  const [commentText, setCommentText] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [pointsEarned, setPointsEarned] = useState<number | null>(null);

  useEffect(() => {
    async function load() {
      try {
        const [postRes, commentsRes] = await Promise.all([
          communityApi.postDetail(postId),
          communityApi.comments(postId, { page: 1, limit: 100 }),
        ]);
        const p = postRes.data.post;
        setPost(p);
        setComments(commentsRes.data.comments ?? []);

        if (p?.artist_id) {
          try {
            const artistRes = await artistsApi.detail(p.artist_id);
            setArtistName(artistRes.data.artist?.name ?? artistRes.data.name ?? "");
          } catch { /* ignore */ }
        }
      } catch {
        setPost(null);
      } finally {
        setLoading(false);
      }
    }
    load();
  }, [postId]);

  const userId = user?.id ?? user?.userId;
  const isOwner = !!(userId && post?.author_id && userId === post.author_id);
  const isAdmin = user?.role === "admin";

  const handleDelete = async () => {
    if (!confirm("정말 삭제하시겠습니까?")) return;
    try {
      await communityApi.deletePost(postId);
      router.push("/community");
    } catch {
      alert("삭제에 실패했습니다.");
    }
  };

  const handleCommentSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!commentText.trim() || submitting) return;
    setSubmitting(true);
    try {
      const res = await communityApi.createComment(postId, { content: commentText.trim() });
      const newComment = res.data.comment;
      setComments((prev) => [...prev, newComment]);
      setCommentText("");
      setPost((p) => p ? { ...p, comment_count: p.comment_count + 1 } : p);
      setPointsEarned(10);
      setTimeout(() => setPointsEarned(null), 3000);
    } catch {
      alert("댓글 작성에 실패했습니다.");
    } finally {
      setSubmitting(false);
    }
  };

  const handleCommentDelete = async (commentId: string) => {
    if (!confirm("댓글을 삭제하시겠습니까?")) return;
    try {
      await communityApi.deleteComment(postId, commentId);
      setComments((prev) => prev.filter((c) => c.id !== commentId));
      setPost((p) => p ? { ...p, comment_count: Math.max(0, p.comment_count - 1) } : p);
    } catch {
      alert("삭제에 실패했습니다.");
    }
  };

  if (loading) {
    return (
      <div className="flex justify-center py-20">
        <div className="inline-block h-6 w-6 animate-spin rounded-full border-2 border-sky-500 border-t-transparent" />
      </div>
    );
  }

  if (!post) {
    return (
      <div className="max-w-3xl mx-auto py-20 text-center">
        <p className="text-slate-400">게시글을 찾을 수 없습니다.</p>
        <Link href="/community" className="mt-4 inline-block text-sm text-sky-500">
          목록으로
        </Link>
      </div>
    );
  }

  return (
    <div className="max-w-3xl mx-auto space-y-6">
      {/* Back link */}
      <Link href="/community" className="text-sm text-slate-400 hover:text-sky-500">
        &larr; 목록으로
      </Link>

      {/* Post */}
      <article className="rounded-xl border border-slate-200 bg-white p-6">
        {/* Header */}
        <div className="flex items-start justify-between gap-4">
          <div>
            {artistName && (
              <span className="mb-2 inline-block rounded-full bg-sky-50 border border-sky-100 px-2.5 py-0.5 text-xs font-medium text-sky-600">
                {artistName}
              </span>
            )}
            {post.is_pinned && (
              <span className="mb-2 ml-1 inline-block rounded-full bg-amber-50 border border-amber-200 px-2.5 py-0.5 text-xs font-medium text-amber-600">
                공지
              </span>
            )}
            <h1 className="text-xl font-bold text-slate-900">{post.title}</h1>
          </div>
          <div className="flex gap-2 shrink-0">
            {isOwner && (
              <Link
                href={`/community/${postId}/edit`}
                className="rounded-lg border border-slate-200 px-3 py-1.5 text-xs text-slate-600 hover:border-sky-300 hover:text-sky-500"
              >
                수정
              </Link>
            )}
            {(isOwner || isAdmin) && (
              <button
                onClick={handleDelete}
                className="rounded-lg border border-red-200 px-3 py-1.5 text-xs text-red-500 hover:bg-red-50"
              >
                삭제
              </button>
            )}
          </div>
        </div>

        {/* Meta */}
        <div className="mt-3 flex items-center gap-3 text-xs text-slate-400">
          <span>{post.author_name}</span>
          <span>{formatDateTime(post.created_at)}</span>
          <span>조회 {post.views}</span>
        </div>

        {/* Content */}
        <div className="mt-6 text-sm text-slate-700 leading-relaxed whitespace-pre-wrap">
          {post.content}
        </div>
      </article>

      {/* Comments section */}
      <section className="rounded-xl border border-slate-200 bg-white">
        <div className="px-6 py-4 border-b border-slate-100">
          <h2 className="text-sm font-semibold text-slate-900">
            댓글 {post.comment_count}
          </h2>
        </div>

        {/* Comment list */}
        {comments.length > 0 && (
          <div className="divide-y divide-slate-50">
            {comments.map((comment) => {
              const commentOwner = userId && comment.author_id === userId;
              return (
                <div key={comment.id} className="px-6 py-3.5">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2 text-xs">
                      <span className="font-medium text-slate-700">{comment.author_name}</span>
                      <span className="text-slate-400">{formatDateTime(comment.created_at)}</span>
                    </div>
                    {(commentOwner || isAdmin) && (
                      <button
                        onClick={() => handleCommentDelete(comment.id)}
                        className="text-xs text-slate-400 hover:text-red-500"
                      >
                        삭제
                      </button>
                    )}
                  </div>
                  <p className="mt-1.5 text-sm text-slate-600">{comment.content}</p>
                </div>
              );
            })}
          </div>
        )}

        {/* Comment input */}
        {user ? (
          <form onSubmit={handleCommentSubmit} className="border-t border-slate-100 p-4">
            {pointsEarned && (
              <div className="mb-3 rounded-lg bg-green-50 border border-green-200 px-4 py-2 text-sm text-green-600">
                +{pointsEarned}pt 적립!
              </div>
            )}
            <div className="flex gap-2">
              <input
                type="text"
                value={commentText}
                onChange={(e) => setCommentText(e.target.value)}
                placeholder="댓글을 입력하세요"
                className="flex-1 rounded-lg border border-slate-200 px-3 py-2 text-sm focus:border-sky-400 focus:outline-none focus:ring-1 focus:ring-sky-400"
              />
              <button
                type="submit"
                disabled={!commentText.trim() || submitting}
                className="rounded-lg bg-sky-500 px-4 py-2 text-sm font-medium text-white hover:bg-sky-600 disabled:opacity-40 transition-colors"
              >
                {submitting ? "..." : "등록"}
              </button>
            </div>
          </form>
        ) : (
          <div className="border-t border-slate-100 p-4 text-center text-sm text-slate-400">
            <Link href="/login" className="text-sky-500 hover:text-sky-600">로그인</Link>
            하시면 댓글을 작성할 수 있습니다
          </div>
        )}
      </section>
    </div>
  );
}
