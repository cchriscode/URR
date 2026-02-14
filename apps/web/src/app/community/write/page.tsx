"use client";

import { useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { communityApi, artistsApi } from "@/lib/api-client";
import { getUser } from "@/lib/storage";
import { AuthGuard } from "@/components/auth-guard";
import type { Artist } from "@/lib/types";

function WriteForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const preselectedArtistId = searchParams.get("artistId") ?? "";
  const user = getUser();

  const [artists, setArtists] = useState<Artist[]>([]);
  const [artistId, setArtistId] = useState(preselectedArtistId);
  const [title, setTitle] = useState("");
  const [content, setContent] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [pointsEarned, setPointsEarned] = useState(false);

  useEffect(() => {
    artistsApi
      .list({ page: 1, limit: 50 })
      .then((res) => setArtists(res.data.artists ?? []))
      .catch(() => setArtists([]));
  }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!artistId) {
      setError("아티스트를 선택해주세요");
      return;
    }
    if (submitting) return;
    setSubmitting(true);
    setError(null);
    try {
      await communityApi.createPost({
        title: title.trim(),
        content: content.trim(),
        artist_id: artistId,
      });
      setPointsEarned(true);
      setTimeout(() => router.push("/community"), 1500);
    } catch {
      setError("글 작성에 실패했습니다.");
      setSubmitting(false);
    }
  };

  if (pointsEarned) {
    return (
      <div className="max-w-2xl mx-auto py-20 text-center">
        <div className="rounded-xl border border-green-200 bg-green-50 p-8">
          <p className="text-lg font-semibold text-green-700">글이 작성되었습니다!</p>
          <p className="mt-2 text-sm text-green-600">+30pt 적립</p>
        </div>
      </div>
    );
  }

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      <h1 className="text-2xl font-bold text-slate-900">글쓰기</h1>

      <form onSubmit={handleSubmit} className="space-y-4">
        {/* Artist select */}
        <div>
          <label className="block text-sm font-medium text-slate-700 mb-1">카테고리 (아티스트)</label>
          <select
            value={artistId}
            onChange={(e) => setArtistId(e.target.value)}
            className="w-full rounded-lg border border-slate-200 px-3 py-2.5 text-sm bg-white focus:border-sky-400 focus:outline-none focus:ring-1 focus:ring-sky-400"
          >
            <option value="">아티스트를 선택하세요</option>
            {artists.map((artist) => (
              <option key={artist.id} value={artist.id}>
                {artist.name}
              </option>
            ))}
          </select>
        </div>

        {/* Title */}
        <div>
          <label className="block text-sm font-medium text-slate-700 mb-1">제목</label>
          <input
            type="text"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="제목을 입력하세요"
            maxLength={255}
            required
            className="w-full rounded-lg border border-slate-200 px-3 py-2.5 text-sm focus:border-sky-400 focus:outline-none focus:ring-1 focus:ring-sky-400"
          />
        </div>

        {/* Content */}
        <div>
          <label className="block text-sm font-medium text-slate-700 mb-1">내용</label>
          <textarea
            value={content}
            onChange={(e) => setContent(e.target.value)}
            placeholder="내용을 입력하세요"
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

        <div className="flex items-center justify-between pt-2">
          <p className="text-xs text-slate-400">글 작성 시 +30pt 적립</p>
          <div className="flex gap-2">
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
              {submitting ? "작성 중..." : "작성"}
            </button>
          </div>
        </div>
      </form>
    </div>
  );
}

export default function WritePage() {
  return (
    <AuthGuard>
      <WriteForm />
    </AuthGuard>
  );
}
