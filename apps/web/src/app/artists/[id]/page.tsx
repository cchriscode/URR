"use client";

import { useEffect, useState } from "react";
import Image from "next/image";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { artistsApi, membershipsApi } from "@/lib/api-client";
import { getUser } from "@/lib/storage";
import { formatDate, formatDateTime, formatPrice } from "@/lib/format";
import type { Artist, ArtistMembership, MembershipPointLog, MembershipTier } from "@/lib/types";

const tierConfig: Record<MembershipTier, { label: string; emoji: string; cls: string; border: string }> = {
  BRONZE: { label: "Bronze", emoji: "\uD83E\uDD49", cls: "bg-slate-100 text-slate-600", border: "border-slate-200" },
  SILVER: { label: "Silver", emoji: "\uD83E\uDD48", cls: "bg-gray-100 text-gray-600", border: "border-gray-200" },
  GOLD: { label: "Gold", emoji: "\uD83E\uDD47", cls: "bg-amber-50 text-amber-600", border: "border-amber-200" },
  DIAMOND: { label: "Diamond", emoji: "\uD83D\uDC8E", cls: "bg-sky-50 text-sky-600", border: "border-sky-200" },
};

const GOLD_THRESHOLD = 500;
const DIAMOND_THRESHOLD = 1500;

interface PreSaleSchedule {
  diamond?: string;
  gold?: string;
  silver?: string;
  bronze?: string;
}

interface ArtistEvent {
  id: string;
  title: string;
  venue?: string;
  event_date?: string;
  sale_start_date?: string;
  status?: string;
  poster_image_url?: string;
  min_price?: number;
  max_price?: number;
  pre_sale_schedule?: PreSaleSchedule;
}

export default function ArtistDetailPage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const artistId = params.id ?? "";
  const [artist, setArtist] = useState<Artist | null>(null);
  const [memberCount, setMemberCount] = useState(0);
  const [events, setEvents] = useState<ArtistEvent[]>([]);
  const [membership, setMembership] = useState<ArtistMembership | null>(null);
  const [pointHistory, setPointHistory] = useState<MembershipPointLog[]>([]);
  const [loading, setLoading] = useState(true);
  const [subscribing, setSubscribing] = useState(false);
  const [subError, setSubError] = useState<string | null>(null);

  useEffect(() => {
    if (!artistId) return;
    let cancelled = false;
    const loggedIn = !!getUser();
    setLoading(true);

    const fetchArtist = artistsApi.detail(artistId).then((res) => {
      if (cancelled) return;
      setArtist(res.data.artist ?? null);
      setMemberCount(res.data.memberCount ?? 0);
      setEvents(res.data.events ?? []);
    });

    const fetchMembership = loggedIn
      ? membershipsApi.myForArtist(artistId).then((res) => {
          if (cancelled) return;
          const m = res.data.membership;
          if (m && m.id) {
            setMembership(m);
          } else {
            setMembership(null);
          }
          setPointHistory(res.data.pointHistory ?? []);
        }).catch(() => {
          if (cancelled) return;
          setMembership(null);
          setPointHistory([]);
        })
      : Promise.resolve();

    Promise.all([fetchArtist, fetchMembership])
      .catch(() => {})
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => { cancelled = true; };
  }, [artistId]);

  const handleSubscribe = async () => {
    if (!artistId) return;
    setSubscribing(true);
    setSubError(null);
    try {
      const res = await membershipsApi.subscribe(artistId);
      const data = res.data;
      if (data.membershipId && data.status === "pending") {
        const qs = new URLSearchParams({
          artistName: data.artistName ?? artist?.name ?? "",
          price: String(data.price ?? artist?.membership_price ?? 30000),
          artistId,
        });
        router.push(`/membership-payment/${data.membershipId}?${qs.toString()}`);
      } else if (data.membership) {
        // Direct activation (fallback)
        setMembership(data.membership);
        setMemberCount((prev) => prev + 1);
      }
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setSubError(msg ?? "멤버십 가입에 실패했습니다.");
    } finally {
      setSubscribing(false);
    }
  };

  if (loading) {
    return (
      <div className="flex justify-center py-20">
        <div className="inline-block h-6 w-6 animate-spin rounded-full border-2 border-sky-500 border-t-transparent" />
      </div>
    );
  }

  if (!artist) {
    return (
      <div className="max-w-2xl mx-auto text-center py-20">
        <p className="text-slate-400">아티스트를 찾을 수 없습니다.</p>
        <Link href="/artists" className="mt-4 inline-block text-sm text-sky-500 hover:text-sky-600">
          목록으로 돌아가기
        </Link>
      </div>
    );
  }

  const effectiveTier: MembershipTier = membership
    ? (membership.effective_tier ?? membership.tier)
    : "BRONZE";
  const tierCfg = tierConfig[effectiveTier];
  const points = membership?.points ?? 0;

  // Progress bar
  let progressPercent = 0;
  let progressLabel = "";
  if (membership) {
    if (effectiveTier === "SILVER") {
      progressPercent = Math.min((points / GOLD_THRESHOLD) * 100, 100);
      progressLabel = `${points} / ${GOLD_THRESHOLD}pt (Gold)`;
    } else if (effectiveTier === "GOLD") {
      progressPercent = Math.min(((points - GOLD_THRESHOLD) / (DIAMOND_THRESHOLD - GOLD_THRESHOLD)) * 100, 100);
      progressLabel = `${points} / ${DIAMOND_THRESHOLD}pt (Diamond)`;
    } else {
      progressPercent = 100;
      progressLabel = `${points}pt (최고 등급)`;
    }
  }

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      {/* Back link */}
      <Link href="/artists" className="text-sm text-slate-500 hover:text-sky-500">
        &larr; 아티스트 목록
      </Link>

      {/* Artist header */}
      <div className="rounded-2xl border border-slate-200 bg-white overflow-hidden">
        <div className="relative h-48 bg-slate-100 flex items-center justify-center overflow-hidden">
          {artist.image_url ? (
            <Image src={artist.image_url} alt={artist.name} className="w-full h-full object-cover" fill sizes="100vw" />
          ) : (
            <span className="text-6xl font-bold text-slate-200">{artist.name.charAt(0)}</span>
          )}
        </div>
        <div className="p-6">
          <h1 className="text-2xl font-bold text-slate-900">{artist.name}</h1>
          {artist.description && (
            <p className="mt-1 text-sm text-slate-500">{artist.description}</p>
          )}
          <div className="mt-3 flex items-center gap-4 text-xs text-slate-400">
            <span>멤버 {memberCount}명</span>
            <span>공연 {events.length}개</span>
          </div>
        </div>
      </div>

      {/* Membership section */}
      <div className="rounded-2xl border border-slate-200 bg-white p-6 space-y-4">
        <h2 className="text-lg font-bold text-slate-900">멤버십</h2>

        {!getUser() ? (
          <div className="rounded-lg bg-slate-50 border border-slate-200 p-4 text-center">
            <p className="text-sm text-slate-500 mb-2">로그인 후 멤버십을 가입할 수 있습니다</p>
            <Link href="/login" className="text-sm text-sky-500 hover:text-sky-600 font-medium">
              로그인하기 &rarr;
            </Link>
          </div>
        ) : !membership ? (
          <div className="space-y-3">
            <div className="rounded-lg bg-slate-50 border border-slate-200 p-4 text-center">
              <p className="text-sm text-slate-600 mb-1">
                <span className="font-medium">{artist.name}</span> 팬클럽 멤버십에 가입하세요
              </p>
              <p className="text-xs text-slate-400 mb-3">
                선예매 접근, 수수료 할인, 양도 기능 등 다양한 혜택을 받으세요
              </p>
              <button
                onClick={handleSubscribe}
                disabled={subscribing}
                className="rounded-lg bg-sky-500 px-5 py-2.5 text-sm font-medium text-white hover:bg-sky-600 disabled:opacity-50 transition-colors"
              >
                {subscribing
                  ? "가입 중..."
                  : `멤버십 가입하기 (${formatPrice(artist.membership_price ?? 30000)}원/년)`}
              </button>
            </div>
            {subError && (
              <p className="rounded-lg bg-red-50 border border-red-200 px-3 py-2 text-sm text-red-600">{subError}</p>
            )}
          </div>
        ) : (
          <div className="space-y-4">
            {/* Current tier display */}
            <div className="flex items-center gap-3">
              <span className={`rounded-full border px-3 py-1 text-sm font-medium ${tierCfg.cls} ${tierCfg.border}`}>
                {tierCfg.emoji} {tierCfg.label}
              </span>
              <span className="text-sm text-slate-600 font-medium">{points}pt</span>
              {membership.status !== "active" && (
                <span className="text-xs text-red-400">(만료됨)</span>
              )}
            </div>

            {/* Progress to next tier */}
            {effectiveTier !== "DIAMOND" && (
              <div>
                <div className="flex justify-between text-xs text-slate-400 mb-1">
                  <span>다음 등급까지</span>
                  <span>{progressLabel}</span>
                </div>
                <div className="h-2 rounded-full bg-slate-100">
                  <div
                    className="h-full rounded-full bg-sky-500 transition-all"
                    style={{ width: `${progressPercent}%` }}
                  />
                </div>
              </div>
            )}

            {/* Membership info */}
            <div className="flex items-center gap-4 text-xs text-slate-400">
              {membership.joined_at && <span>가입일: {formatDate(membership.joined_at)}</span>}
              {membership.expires_at && <span>만료일: {formatDate(membership.expires_at)}</span>}
            </div>

            {/* Point history */}
            {pointHistory.length > 0 && (
              <div>
                <h3 className="text-sm font-medium text-slate-700 mb-2">포인트 내역</h3>
                <div className="rounded-lg border border-slate-200 overflow-hidden">
                  <table className="w-full text-xs">
                    <thead className="bg-slate-50">
                      <tr>
                        <th className="px-3 py-2 text-left font-medium text-slate-500">활동</th>
                        <th className="px-3 py-2 text-right font-medium text-slate-500">포인트</th>
                        <th className="px-3 py-2 text-right font-medium text-slate-500">일시</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {pointHistory.map((log) => (
                        <tr key={log.id}>
                          <td className="px-3 py-2 text-slate-600">
                            {log.description ?? log.action_type}
                          </td>
                          <td className="px-3 py-2 text-right font-medium text-green-600">+{log.points}</td>
                          <td className="px-3 py-2 text-right text-slate-400">
                            {formatDate(log.created_at)}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
          </div>
        )}
      </div>

      {/* Tier benefits comparison */}
      <div className="rounded-2xl border border-slate-200 bg-white p-6">
        <h2 className="text-lg font-bold text-slate-900 mb-4">등급별 혜택</h2>
        <div className="overflow-x-auto">
          <table className="w-full text-xs">
            <thead>
              <tr className="border-b border-slate-200">
                <th className="px-3 py-2 text-left font-medium text-slate-500" />
                {(["BRONZE", "SILVER", "GOLD", "DIAMOND"] as MembershipTier[]).map((t) => {
                  const c = tierConfig[t];
                  return (
                    <th key={t} className={`px-3 py-2 text-center font-medium ${effectiveTier === t ? "text-sky-600" : "text-slate-500"}`}>
                      <span className={`inline-block rounded-full border px-2 py-0.5 ${c.cls} ${c.border}`}>
                        {c.emoji} {c.label}
                      </span>
                    </th>
                  );
                })}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              <tr>
                <td className="px-3 py-2.5 font-medium text-slate-600">예매</td>
                <td className="px-3 py-2.5 text-center text-slate-500">일반예매</td>
                <td className="px-3 py-2.5 text-center text-slate-500">선예매 3</td>
                <td className="px-3 py-2.5 text-center text-slate-500">선예매 2</td>
                <td className="px-3 py-2.5 text-center text-slate-500">선예매 1</td>
              </tr>
              <tr>
                <td className="px-3 py-2.5 font-medium text-slate-600">수수료</td>
                <td className="px-3 py-2.5 text-center text-slate-500">기본</td>
                <td className="px-3 py-2.5 text-center text-slate-500">+3,000원</td>
                <td className="px-3 py-2.5 text-center text-slate-500">+2,000원</td>
                <td className="px-3 py-2.5 text-center text-slate-500">+1,000원</td>
              </tr>
              <tr>
                <td className="px-3 py-2.5 font-medium text-slate-600">양도</td>
                <td className="px-3 py-2.5 text-center text-red-400">불가</td>
                <td className="px-3 py-2.5 text-center text-slate-500">가능 (10%)</td>
                <td className="px-3 py-2.5 text-center text-slate-500">가능 (5%)</td>
                <td className="px-3 py-2.5 text-center text-slate-500">가능 (5%)</td>
              </tr>
              <tr>
                <td className="px-3 py-2.5 font-medium text-slate-600">조건</td>
                <td className="px-3 py-2.5 text-center text-slate-400 text-[10px]">회원가입</td>
                <td className="px-3 py-2.5 text-center text-slate-400 text-[10px]">멤버십 가입</td>
                <td className="px-3 py-2.5 text-center text-slate-400 text-[10px]">500pt 이상</td>
                <td className="px-3 py-2.5 text-center text-slate-400 text-[10px]">1,500pt 이상</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      {/* Events by this artist */}
      {events.length > 0 && (
        <div className="rounded-2xl border border-slate-200 bg-white p-6">
          <h2 className="text-lg font-bold text-slate-900 mb-4">공연 일정</h2>
          <div className="space-y-3">
            {events.map((event) => {
              const schedule = event.pre_sale_schedule;
              return (
                <Link
                  key={event.id}
                  href={`/events/${event.id}`}
                  className="block rounded-lg border border-slate-200 p-4 hover:border-sky-300 transition-colors"
                >
                  <div className="flex justify-between items-start">
                    <div>
                      <p className="font-medium text-slate-900 text-sm">{event.title}</p>
                      <div className="mt-1 flex items-center gap-2 text-xs text-slate-400">
                        {event.venue && <span>{event.venue}</span>}
                        {event.event_date && <span>{formatDateTime(event.event_date)}</span>}
                      </div>
                    </div>
                    {event.status && (
                      <span className={`shrink-0 rounded-full px-2 py-0.5 text-xs font-medium ${
                        event.status === "on_sale" ? "bg-sky-50 text-sky-600" :
                        event.status === "upcoming" ? "bg-amber-50 text-amber-600" :
                        "bg-slate-100 text-slate-500"
                      }`}>
                        {event.status === "on_sale" ? "예매 중" : event.status === "upcoming" ? "오픈 예정" : event.status}
                      </span>
                    )}
                  </div>
                  {/* Pre-sale schedule */}
                  {schedule && (
                    <div className="mt-2 grid grid-cols-4 gap-1 text-[10px]">
                      {(["diamond", "gold", "silver", "bronze"] as const).map((t) => {
                        const time = schedule[t];
                        const labels = { diamond: "Diamond", gold: "Gold", silver: "Silver", bronze: "일반" };
                        return (
                          <div key={t} className="rounded bg-slate-50 px-1.5 py-1 text-center">
                            <span className="text-slate-400 block">{labels[t]}</span>
                            <span className="text-slate-600 font-medium">
                              {time ? new Date(time).toLocaleString("ko-KR", { month: "numeric", day: "numeric", hour: "2-digit", minute: "2-digit" }) : "-"}
                            </span>
                          </div>
                        );
                      })}
                    </div>
                  )}
                </Link>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}
