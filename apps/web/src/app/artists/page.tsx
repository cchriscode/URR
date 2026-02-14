"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { artistsApi } from "@/lib/api-client";
import type { Artist } from "@/lib/types";

export default function ArtistsPage() {
  const [artists, setArtists] = useState<Artist[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    artistsApi
      .list({ page: 1, limit: 50 })
      .then((res) => setArtists(res.data.artists ?? []))
      .catch(() => setArtists([]))
      .finally(() => setLoading(false));
  }, []);

  return (
    <div className="max-w-5xl mx-auto">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-slate-900">아티스트</h1>
        <p className="text-sm text-slate-400">{artists.length}개 아티스트</p>
      </div>

      {loading ? (
        <div className="flex justify-center py-16">
          <div className="inline-block h-6 w-6 animate-spin rounded-full border-2 border-sky-500 border-t-transparent" />
        </div>
      ) : artists.length === 0 ? (
        <div className="rounded-2xl border border-slate-200 bg-white p-10 text-center">
          <p className="text-slate-400 text-sm">등록된 아티스트가 없습니다</p>
        </div>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {artists.map((artist) => (
            <Link
              key={artist.id}
              href={`/artists/${artist.id}`}
              className="group block rounded-xl border border-slate-200 bg-white overflow-hidden transition-all hover:border-sky-300 hover:shadow-sm"
            >
              <div className="relative h-40 bg-slate-100 flex items-center justify-center overflow-hidden">
                {artist.image_url ? (
                  <img
                    src={artist.image_url}
                    alt={artist.name}
                    className="w-full h-full object-cover group-hover:scale-105 transition-transform"
                  />
                ) : (
                  <span className="text-4xl font-bold text-slate-300">
                    {artist.name.charAt(0)}
                  </span>
                )}
              </div>
              <div className="p-4">
                <p className="font-semibold text-slate-900 group-hover:text-sky-600 transition-colors">
                  {artist.name}
                </p>
                {artist.description && (
                  <p className="mt-1 text-xs text-slate-400 line-clamp-1">{artist.description}</p>
                )}
                <div className="mt-2 flex items-center gap-3 text-xs text-slate-400">
                  {artist.event_count != null && (
                    <span className="rounded-full bg-sky-50 border border-sky-100 px-2 py-0.5 text-sky-600">
                      공연 {artist.event_count}
                    </span>
                  )}
                  {artist.member_count != null && artist.member_count > 0 && (
                    <span>멤버 {artist.member_count}</span>
                  )}
                </div>
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
