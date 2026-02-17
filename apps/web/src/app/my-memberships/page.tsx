"use client";

import Link from "next/link";
import Image from "next/image";
import { useEffect, useState } from "react";
import { AuthGuard } from "@/components/auth-guard";
import { membershipsApi } from "@/lib/api-client";
import { formatDate } from "@/lib/format";
import type { ArtistMembership, MembershipTier } from "@/lib/types";

const tierConfig: Record<MembershipTier, { label: string; cls: string }> = {
  BRONZE: { label: "Bronze", cls: "bg-slate-100 text-slate-600 border-slate-200" },
  SILVER: { label: "Silver", cls: "bg-gray-100 text-gray-600 border-gray-200" },
  GOLD: { label: "Gold", cls: "bg-amber-50 text-amber-600 border-amber-200" },
  DIAMOND: { label: "Diamond", cls: "bg-sky-50 text-sky-600 border-sky-200" },
};

export default function MyMembershipsPage() {
  const [memberships, setMemberships] = useState<ArtistMembership[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    membershipsApi
      .my()
      .then((res) => setMemberships(res.data.memberships ?? []))
      .catch(() => setMemberships([]))
      .finally(() => setLoading(false));
  }, []);

  return (
    <AuthGuard>
      <div className="max-w-2xl mx-auto">
        <h1 className="text-2xl font-bold text-slate-900 mb-6">내 멤버십</h1>

        {loading ? (
          <div className="flex justify-center py-16">
            <div className="inline-block h-6 w-6 animate-spin rounded-full border-2 border-sky-500 border-t-transparent" />
          </div>
        ) : memberships.length === 0 ? (
          <div className="rounded-2xl border border-slate-200 bg-white p-10 text-center">
            <p className="text-slate-400 text-sm mb-4">아직 가입한 멤버십이 없습니다</p>
            <Link
              href="/artists"
              className="inline-block rounded-lg bg-sky-500 px-4 py-2 text-sm font-medium text-white hover:bg-sky-600 transition-colors"
            >
              아티스트 둘러보기
            </Link>
          </div>
        ) : (
          <div className="space-y-3">
            {memberships.map((m) => {
              const tier = (m.effective_tier ?? m.tier) as MembershipTier;
              const cfg = tierConfig[tier] ?? tierConfig.BRONZE;
              return (
                <Link
                  key={m.id}
                  href={`/artists/${m.artist_id}`}
                  className="block rounded-xl border border-slate-200 bg-white p-5 transition-all hover:border-sky-300 hover:shadow-sm"
                >
                  <div className="flex items-center gap-4">
                    {/* Artist image or initial */}
                    <div className="h-12 w-12 shrink-0 rounded-full bg-slate-100 flex items-center justify-center overflow-hidden">
                      {m.artist_image_url ? (
                        <Image src={m.artist_image_url} alt="" width={48} height={48} className="h-full w-full object-cover" />
                      ) : (
                        <span className="text-lg font-bold text-slate-300">
                          {(m.artist_name ?? "?").charAt(0)}
                        </span>
                      )}
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2">
                        <p className="font-medium text-slate-900">{m.artist_name ?? "Unknown"}</p>
                        <span className={`shrink-0 rounded-full border px-2 py-0.5 text-xs font-medium ${cfg.cls}`}>
                          {cfg.label}
                        </span>
                      </div>
                      <div className="mt-1 flex items-center gap-3 text-xs text-slate-400">
                        <span>{m.points}pt</span>
                        {m.expires_at && <span>만료 {formatDate(m.expires_at)}</span>}
                        <span className={m.status === "active" ? "text-green-500" : "text-red-400"}>
                          {m.status === "active" ? "활성" : "만료됨"}
                        </span>
                      </div>
                    </div>
                    <span className="text-slate-300 text-sm">&rarr;</span>
                  </div>
                </Link>
              );
            })}
          </div>
        )}
      </div>
    </AuthGuard>
  );
}
