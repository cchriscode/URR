"use client";

import Link from "next/link";
import Image from "next/image";
import { useState } from "react";
import { useEvents } from "@/hooks/use-events";
import { useCountdown, formatCountdown } from "@/hooks/use-countdown";
import { formatEventDate, formatPrice } from "@/lib/format";
import { Spinner } from "@/components/ui/Spinner";
import type { EventSummary } from "@/lib/types";

const filters = [
  { value: "on_sale", label: "예매 중" },
  { value: "upcoming", label: "오픈 예정" },
  { value: "ended", label: "예매 종료" },
  { value: "cancelled", label: "취소됨" },
  { value: "", label: "전체" },
];

function statusBadge(status?: string) {
  switch (status) {
    case "on_sale":
      return { text: "예매 중", cls: "bg-sky-50 text-sky-600" };
    case "upcoming":
      return { text: "오픈 예정", cls: "bg-amber-50 text-amber-600" };
    case "ended":
      return { text: "종료", cls: "bg-slate-100 text-slate-500" };
    case "cancelled":
      return { text: "취소", cls: "bg-red-50 text-red-500" };
    case "sold_out":
      return { text: "매진", cls: "bg-red-50 text-red-500" };
    default:
      return { text: status ?? "대기", cls: "bg-slate-100 text-slate-500" };
  }
}

interface EventWithPrice extends EventSummary {
  min_price?: number;
  max_price?: number;
  poster_image_url?: string;
  sale_start_date?: string;
  sale_end_date?: string;
}

function EventCard({ event, onExpire }: { event: EventWithPrice; onExpire: () => void }) {
  const countdownTarget =
    event.status === "on_sale"
      ? event.sale_end_date
      : event.status === "upcoming"
        ? event.sale_start_date
        : null;

  const showMonths = event.status === "upcoming";
  const timeLeft = useCountdown(countdownTarget, onExpire);
  const badge = statusBadge(event.status);

  return (
    <Link
      href={`/events/${event.id}`}
      className="group rounded-xl border border-slate-200 bg-white overflow-hidden transition-all hover:border-sky-300 hover:shadow-sm"
    >
      {/* Poster */}
      <div className="relative h-40 bg-slate-100 flex items-center justify-center">
        {event.poster_image_url ? (
          <Image src={event.poster_image_url} alt={event.title} className="object-cover" fill sizes="(max-width: 640px) 100vw, (max-width: 1024px) 50vw, 33vw" />
        ) : (
          <span className="text-4xl">&#127915;</span>
        )}
        <span className={`absolute top-2 right-2 rounded-full px-2.5 py-0.5 text-xs font-medium ${badge.cls}`}>
          {badge.text}
        </span>
      </div>

      {/* Countdown */}
      {countdownTarget && !timeLeft.isExpired && (
        <div
          className={`px-4 py-2 text-xs font-medium flex items-center gap-1.5 ${
            event.status === "on_sale" ? "bg-sky-50 text-sky-700" : "bg-amber-50 text-amber-700"
          }`}
        >
          <span>{event.status === "on_sale" ? "\u23F0" : "\uD83C\uDFAF"}</span>
          <span>
            {event.status === "on_sale" ? "판매 종료까지 " : "오픈까지 "}
            {formatCountdown(timeLeft, showMonths)}
          </span>
        </div>
      )}
      {countdownTarget && timeLeft.isExpired && (
        <div className="px-4 py-2 text-xs font-medium bg-slate-50 text-slate-500">
          {event.status === "on_sale" ? "\u23F0 판매 종료" : "\uD83C\uDF89 판매 시작!"}
        </div>
      )}

      {/* Info */}
      <div className="p-4">
        <p className="font-semibold text-slate-900 group-hover:text-sky-600 transition-colors line-clamp-1">
          {event.title}
        </p>
        <div className="mt-2 space-y-1 text-sm text-slate-500">
          {event.venue ? <p>&#128205; {event.venue}</p> : null}
          {event.event_date ? <p>&#128197; {formatEventDate(event.event_date)}</p> : null}
          {event.min_price != null && event.max_price != null ? (
            <p>&#128176; {formatPrice(event.min_price)}원 ~ {formatPrice(event.max_price)}원</p>
          ) : null}
        </div>
        {event.artist_name ? (
          <span className="mt-3 inline-block rounded-full bg-amber-50 px-2.5 py-0.5 text-xs font-medium text-amber-600">
            {event.artist_name}
          </span>
        ) : null}
      </div>
    </Link>
  );
}

interface HomePageClientProps {
  initialEvents: EventWithPrice[];
}

export function HomePageClient({ initialEvents }: HomePageClientProps) {
  const [filter, setFilter] = useState("on_sale");

  const params: Record<string, string | number> = { page: 1, limit: 12 };
  if (filter) params.status = filter;

  const { data: events = initialEvents, isLoading, error, refetch } = useEvents(params);

  return (
    <div className="space-y-6">
      {/* Hero */}
      <section className="rounded-2xl bg-white border border-slate-200 p-8 text-center">
        <div className="text-4xl mb-2">
          <span className="text-amber-400">&#9889;</span>
        </div>
        <h1 className="text-3xl font-bold tracking-tight text-slate-900">우르르, 가장 빠른 티켓팅</h1>
        <p className="mt-2 text-slate-500">원하는 공연을 빠르게 검색하고 바로 예매하세요</p>
      </section>

      {/* Filter tabs */}
      <div className="flex gap-2 overflow-x-auto">
        {filters.map((f) => (
          <button
            key={f.value}
            onClick={() => setFilter(f.value)}
            className={`shrink-0 rounded-lg px-4 py-2 text-sm font-medium transition-colors ${
              filter === f.value
                ? "bg-sky-500 text-white"
                : "bg-white border border-slate-200 text-slate-600 hover:border-sky-300 hover:text-sky-500"
            }`}
          >
            {f.label}
          </button>
        ))}
      </div>

      {/* Events grid */}
      {isLoading ? (
        <div className="flex justify-center py-16">
          <Spinner size="sm" className="border-sky-500 border-t-transparent" />
        </div>
      ) : error ? (
        <div className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-600">이벤트를 불러오는데 실패했습니다.</div>
      ) : events.length === 0 ? (
        <div className="rounded-xl bg-white border border-slate-200 p-10 text-center">
          <p className="text-slate-400">이벤트가 없습니다</p>
        </div>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {events.map((event: EventWithPrice) => (
            <EventCard
              key={event.id}
              event={event}
              onExpire={
                event.status === "upcoming"
                  ? () => setFilter("on_sale")
                  : () => refetch()
              }
            />
          ))}
        </div>
      )}
    </div>
  );
}
