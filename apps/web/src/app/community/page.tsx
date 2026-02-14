"use client";

import Link from "next/link";
import { useEffect, useState, useCallback } from "react";
import { communityApi, artistsApi } from "@/lib/api-client";
import { getUser } from "@/lib/storage";
import { formatDate } from "@/lib/format";
import type { CommunityPost, Artist } from "@/lib/types";

export default function CommunityPage() {
  const [posts, setPosts] = useState<CommunityPost[]>([]);
  const [artists, setArtists] = useState<Artist[]>([]);
  const [selectedArtist, setSelectedArtist] = useState<string>("");
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [loading, setLoading] = useState(true);
  const user = getUser();

  useEffect(() => {
    artistsApi
      .list({ page: 1, limit: 50 })
      .then((res) => setArtists(res.data.artists ?? []))
      .catch(() => setArtists([]));
  }, []);

  const fetchPosts = useCallback(async () => {
    setLoading(true);
    try {
      const params: Record<string, string | number> = { page, limit: 20 };
      if (selectedArtist) params.artistId = selectedArtist;
      const res = await communityApi.posts(params);
      setPosts(res.data.posts ?? []);
      setTotalPages(res.data.pagination?.totalPages ?? 1);
    } catch {
      setPosts([]);
    } finally {
      setLoading(false);
    }
  }, [selectedArtist, page]);

  useEffect(() => {
    fetchPosts();
  }, [fetchPosts]);

  const handleArtistSelect = (artistId: string) => {
    setSelectedArtist(artistId);
    setPage(1);
  };

  const selectedArtistName = artists.find((a) => a.id === selectedArtist)?.name;

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-slate-900">커뮤니티</h1>
        {user && (
          <Link
            href={`/community/write${selectedArtist ? `?artistId=${selectedArtist}` : ""}`}
            className="rounded-lg bg-sky-500 px-4 py-2 text-sm font-medium text-white hover:bg-sky-600 transition-colors"
          >
            글쓰기
          </Link>
        )}
      </div>

      {/* Artist category bar */}
      <div className="flex gap-2 overflow-x-auto pb-1">
        <button
          onClick={() => handleArtistSelect("")}
          className={`shrink-0 rounded-lg px-4 py-2 text-sm font-medium transition-colors ${
            selectedArtist === ""
              ? "bg-sky-500 text-white"
              : "bg-white border border-slate-200 text-slate-600 hover:border-sky-300 hover:text-sky-500"
          }`}
        >
          전체
        </button>
        {artists.map((artist) => (
          <button
            key={artist.id}
            onClick={() => handleArtistSelect(artist.id)}
            className={`shrink-0 rounded-lg px-4 py-2 text-sm font-medium transition-colors ${
              selectedArtist === artist.id
                ? "bg-sky-500 text-white"
                : "bg-white border border-slate-200 text-slate-600 hover:border-sky-300 hover:text-sky-500"
            }`}
          >
            {artist.name}
          </button>
        ))}
      </div>

      {/* Posts list */}
      {loading ? (
        <div className="flex justify-center py-16">
          <div className="inline-block h-6 w-6 animate-spin rounded-full border-2 border-sky-500 border-t-transparent" />
        </div>
      ) : posts.length === 0 ? (
        <div className="rounded-xl bg-white border border-slate-200 p-10 text-center">
          <p className="text-slate-400">
            {selectedArtistName
              ? `${selectedArtistName} 커뮤니티에 아직 글이 없습니다`
              : "아직 글이 없습니다"}
          </p>
          {user && (
            <Link
              href={`/community/write${selectedArtist ? `?artistId=${selectedArtist}` : ""}`}
              className="mt-3 inline-block text-sm text-sky-500 hover:text-sky-600"
            >
              첫 글을 작성해보세요
            </Link>
          )}
        </div>
      ) : (
        <div className="rounded-xl border border-slate-200 bg-white overflow-hidden divide-y divide-slate-100">
          {posts.map((post) => {
            const artistName = artists.find((a) => a.id === post.artist_id)?.name;
            return (
              <Link
                key={post.id}
                href={`/community/${post.id}`}
                className="flex items-center gap-4 px-5 py-3.5 hover:bg-slate-50 transition-colors"
              >
                {/* Pin badge or artist badge */}
                <div className="shrink-0 w-16">
                  {post.is_pinned ? (
                    <span className="rounded-full bg-amber-50 border border-amber-200 px-2 py-0.5 text-xs font-medium text-amber-600">
                      공지
                    </span>
                  ) : artistName && !selectedArtist ? (
                    <span className="rounded-full bg-sky-50 border border-sky-100 px-2 py-0.5 text-xs text-sky-600 truncate block">
                      {artistName}
                    </span>
                  ) : null}
                </div>

                {/* Title */}
                <p className="flex-1 text-sm font-medium text-slate-800 truncate">
                  {post.title}
                  {post.comment_count > 0 && (
                    <span className="ml-1.5 text-sky-500 font-normal">[{post.comment_count}]</span>
                  )}
                </p>

                {/* Meta */}
                <div className="shrink-0 flex items-center gap-3 text-xs text-slate-400">
                  <span>{post.author_name}</span>
                  <span>{post.views}</span>
                  <span>{formatDate(post.created_at)}</span>
                </div>
              </Link>
            );
          })}
        </div>
      )}

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex justify-center gap-2">
          <button
            onClick={() => setPage((p) => Math.max(1, p - 1))}
            disabled={page === 1}
            className="rounded-lg border border-slate-200 px-3 py-1.5 text-sm text-slate-600 disabled:opacity-40 hover:border-sky-300"
          >
            이전
          </button>
          {Array.from({ length: totalPages }, (_, i) => i + 1)
            .filter((p) => Math.abs(p - page) <= 2)
            .map((p) => (
              <button
                key={p}
                onClick={() => setPage(p)}
                className={`rounded-lg px-3 py-1.5 text-sm font-medium ${
                  p === page
                    ? "bg-sky-500 text-white"
                    : "border border-slate-200 text-slate-600 hover:border-sky-300"
                }`}
              >
                {p}
              </button>
            ))}
          <button
            onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
            disabled={page === totalPages}
            className="rounded-lg border border-slate-200 px-3 py-1.5 text-sm text-slate-600 disabled:opacity-40 hover:border-sky-300"
          >
            다음
          </button>
        </div>
      )}
    </div>
  );
}
