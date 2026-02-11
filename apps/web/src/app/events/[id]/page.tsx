"use client";

import Link from "next/link";
import { useEffect, useState, useCallback } from "react";
import { useParams } from "next/navigation";
import { eventsApi } from "@/lib/api-client";
import { useCountdown, formatCountdown } from "@/hooks/use-countdown";
import { formatEventDate } from "@/lib/format";
import type { TicketType } from "@/lib/types";

interface EventDetail {
  id: string;
  title?: string;
  description?: string;
  venue?: string;
  address?: string;
  event_date?: string;
  eventDate?: string;
  sale_start_date?: string;
  saleStartDate?: string;
  sale_end_date?: string;
  saleEndDate?: string;
  poster_image_url?: string;
  posterImageUrl?: string;
  artist_name?: string;
  artistName?: string;
  status?: string;
  ticket_types?: TicketType[];
  ticketTypes?: TicketType[];
}

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

export default function EventDetailPage() {
  const params = useParams<{ id: string }>();
  const [event, setEvent] = useState<EventDetail | null>(null);
  const [loading, setLoading] = useState(true);

  const fetchEvent = useCallback(() => {
    if (!params.id) return;
    eventsApi
      .detail(params.id)
      .then((res) => {
        const ev = res.data.event ?? res.data.data ?? res.data ?? null;
        if (ev && !ev.ticketTypes && !ev.ticket_types && res.data.ticketTypes) {
          ev.ticketTypes = res.data.ticketTypes;
        }
        setEvent(ev);
      })
      .finally(() => setLoading(false));
  }, [params.id]);

  useEffect(() => {
    fetchEvent();
  }, [fetchEvent]);

  if (loading) {
    return (
      <div className="flex justify-center py-20">
        <div className="inline-block h-6 w-6 animate-spin rounded-full border-2 border-sky-500 border-t-transparent" />
      </div>
    );
  }

  if (!event) {
    return (
      <div className="max-w-2xl mx-auto text-center py-20">
        <p className="text-slate-400">이벤트를 찾을 수 없습니다.</p>
        <Link href="/" className="mt-4 inline-block text-sm text-sky-500 hover:text-sky-600">홈으로 돌아가기</Link>
      </div>
    );
  }

  const badge = statusBadge(event.status);
  const poster = event.poster_image_url ?? event.posterImageUrl;
  const artist = event.artist_name ?? event.artistName;
  const eventDate = event.event_date ?? event.eventDate;
  const saleStart = event.sale_start_date ?? event.saleStartDate;
  const saleEnd = event.sale_end_date ?? event.saleEndDate;
  const tickets = event.ticket_types ?? event.ticketTypes ?? [];

  return (
    <EventDetailContent
      event={event}
      badge={badge}
      poster={poster}
      artist={artist}
      eventDate={eventDate}
      saleStart={saleStart}
      saleEnd={saleEnd}
      tickets={tickets}
      refetch={fetchEvent}
    />
  );
}

function EventDetailContent({
  event,
  badge,
  poster,
  artist,
  eventDate,
  saleStart,
  saleEnd,
  tickets,
  refetch,
}: {
  event: EventDetail;
  badge: { text: string; cls: string };
  poster?: string;
  artist?: string;
  eventDate?: string;
  saleStart?: string;
  saleEnd?: string;
  tickets: TicketType[];
  refetch: () => void;
}) {
  const countdownTarget =
    event.status === "on_sale"
      ? saleEnd
      : event.status === "upcoming"
        ? saleStart
        : null;
  const countdownLabel = event.status === "on_sale" ? "판매 종료까지" : "판매 시작까지";
  const showMonths = event.status === "upcoming";

  // upcoming 상태에서 카운트다운 만료 시 재조회
  const timeLeft = useCountdown(
    countdownTarget,
    event.status === "upcoming" ? refetch : undefined,
  );

  // 서버 status가 on_sale이거나, upcoming이지만 카운트다운이 이미 만료된 경우 예매 버튼 활성화
  const canBook =
    event.status === "on_sale" ||
    (event.status === "upcoming" && timeLeft.isExpired);

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      {/* Poster */}
      {poster && (
        <div className="rounded-2xl overflow-hidden border border-slate-200 bg-slate-100 h-64 flex items-center justify-center">
          <img src={poster} alt={event.title} className="h-full w-full object-cover" />
        </div>
      )}

      {/* Main Info */}
      <div className="rounded-2xl border border-slate-200 bg-white p-6">
        <div className="flex items-start justify-between mb-4">
          <div>
            <h1 className="text-2xl font-bold text-slate-900">{event.title}</h1>
            {artist && <p className="mt-1 text-sm text-amber-600 font-medium">{artist}</p>}
          </div>
          <span className={`shrink-0 rounded-full px-2.5 py-0.5 text-xs font-medium ${badge.cls}`}>
            {badge.text}
          </span>
        </div>

        {/* Countdown chip */}
        {countdownTarget && !timeLeft.isExpired && (
          <div
            className={`mb-4 inline-flex items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium ${
              event.status === "on_sale"
                ? "bg-sky-50 text-sky-700 border border-sky-100"
                : "bg-amber-50 text-amber-700 border border-amber-100"
            }`}
          >
            <span>{event.status === "on_sale" ? "\u23F0" : "\uD83C\uDFAF"}</span>
            {countdownLabel} {formatCountdown(timeLeft, showMonths)}
          </div>
        )}

        <div className="space-y-3 mt-2">
          <div className="flex items-center gap-3 text-sm">
            <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-slate-100 text-slate-500">&#128205;</span>
            <div>
              <p className="text-xs text-slate-400">장소</p>
              <p className="text-slate-700">{event.venue ?? "-"}</p>
              {event.address && <p className="text-xs text-slate-400">{event.address}</p>}
            </div>
          </div>
          <div className="flex items-center gap-3 text-sm">
            <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-slate-100 text-slate-500">&#128197;</span>
            <div>
              <p className="text-xs text-slate-400">공연일시</p>
              <p className="text-slate-700">{formatEventDate(eventDate)}</p>
            </div>
          </div>
          {(saleStart || saleEnd) && (
            <div className="flex items-center gap-3 text-sm">
              <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-slate-100 text-slate-500">&#128337;</span>
              <div>
                <p className="text-xs text-slate-400">판매 기간</p>
                <p className="text-slate-700">{formatEventDate(saleStart)} ~ {formatEventDate(saleEnd)}</p>
              </div>
            </div>
          )}
        </div>

        {event.description && (
          <p className="mt-6 text-sm text-slate-600 leading-relaxed border-t border-slate-100 pt-4">{event.description}</p>
        )}
      </div>

      {/* Ticket Types */}
      {tickets.length > 0 && (
        <div className="rounded-2xl border border-slate-200 bg-white p-6">
          <h2 className="text-sm font-medium text-slate-700 mb-4">티켓 종류</h2>
          <div className="space-y-2">
            {tickets.map((tt) => (
              <div key={tt.id} className="flex items-center justify-between rounded-lg bg-slate-50 border border-slate-100 px-4 py-3">
                <div>
                  <p className="text-sm font-medium text-slate-900">{tt.name}</p>
                  {tt.description && <p className="text-xs text-slate-400">{tt.description}</p>}
                </div>
                <div className="text-right">
                  <p className="text-sm font-bold text-sky-600">{tt.price.toLocaleString()}원</p>
                  <p className="text-xs text-slate-400">
                    잔여 {tt.available_quantity}/{tt.total_quantity}
                  </p>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* CTA */}
      {canBook && event.id && (
        <Link
          href={`/queue/${event.id}`}
          className="block w-full rounded-xl bg-sky-500 px-6 py-3.5 text-center font-medium text-white hover:bg-sky-600 transition-colors"
        >
          예매하기
        </Link>
      )}
      {event.status === "upcoming" && !timeLeft.isExpired && (
        <div className="rounded-xl bg-amber-50 border border-amber-200 px-6 py-3.5 text-center text-sm text-amber-700">
          아직 판매가 시작되지 않았습니다. {saleStart && `판매 시작: ${formatEventDate(saleStart)}`}
        </div>
      )}
    </div>
  );
}
