"use client";

import { useEffect, useState, useCallback } from "react";
import { useParams, useRouter } from "next/navigation";
import axios from "axios";
import { AuthGuard } from "@/components/auth-guard";
import { eventsApi, reservationsApi } from "@/lib/api-client";
import { formatPrice } from "@/lib/format";
import type { EventDetail, TicketType } from "@/lib/types";

export default function StandingBookPage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const eventId = params.id ?? "";

  const [event, setEvent] = useState<EventDetail | null>(null);
  const [tickets, setTickets] = useState<TicketType[]>([]);
  const [selected, setSelected] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [booking, setBooking] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchEvent = useCallback(() => {
    if (!eventId) return;
    eventsApi
      .detail(eventId)
      .then(({ data }) => {
        const ev: EventDetail = data.event ?? data.data ?? data ?? null;
        if (ev) {
          if (!ev.ticketTypes && !ev.ticket_types && data.ticketTypes) {
            ev.ticketTypes = data.ticketTypes;
          }
          setEvent(ev);
          setTickets(ev.ticket_types ?? ev.ticketTypes ?? []);
        }
      })
      .finally(() => setLoading(false));
  }, [eventId]);

  useEffect(() => {
    fetchEvent();
  }, [fetchEvent]);

  const selectedTicket = tickets.find((t) => t.id === selected);

  const handleBook = async () => {
    if (!selected || !selectedTicket) return;
    if (selectedTicket.available_quantity <= 0) {
      setError("매진된 티켓입니다.");
      return;
    }
    setBooking(true);
    setError(null);
    try {
      const res = await reservationsApi.createTicketOnly({
        eventId,
        items: [{ ticketTypeId: selected, quantity: 1 }],
      });
      const reservationId =
        res.data?.id ?? res.data?.reservation?.id ?? res.data?.data?.id;
      if (reservationId) {
        router.push(`/payment/${reservationId}`);
      } else {
        router.push("/my-reservations");
      }
    } catch (err: unknown) {
      if (axios.isAxiosError(err)) {
        const status = err.response?.status;
        const data = err.response?.data as
          | { message?: string; error?: string }
          | undefined;
        if (status === 409) {
          setError("매진되었습니다. 다른 티켓을 선택해주세요.");
          fetchEvent(); // refresh availability
          setSelected(null);
        } else {
          setError(
            data?.message ?? data?.error ?? "예매에 실패했습니다. 다시 시도해주세요.",
          );
        }
      } else {
        setError("네트워크 오류가 발생했습니다. 다시 시도해주세요.");
      }
    } finally {
      setBooking(false);
    }
  };

  const artist = event?.artist_name ?? event?.artistName;
  const title = event?.title;

  return (
    <AuthGuard>
      <div className="max-w-lg mx-auto space-y-6">
        {/* Header */}
        <div>
          {title && (
            <h1 className="text-xl font-bold text-slate-900">{title}</h1>
          )}
          {artist && (
            <p className="mt-0.5 text-sm text-amber-600 font-medium">{artist}</p>
          )}
        </div>

        {/* Standing badge */}
        <div className="flex items-center gap-2 rounded-xl bg-sky-50 border border-sky-100 px-4 py-3">
          <span className="text-lg">🎪</span>
          <div>
            <p className="text-sm font-medium text-sky-800">스탠딩 공연</p>
            <p className="text-xs text-sky-600">좌석 지정 없이 자유 입장 가능합니다</p>
          </div>
        </div>

        {/* Ticket selection */}
        <div className="rounded-2xl border border-slate-200 bg-white p-6">
          <h2 className="text-sm font-medium text-slate-700 mb-4">티켓 종류 선택</h2>

          {loading ? (
            <div className="flex justify-center py-8">
              <div className="inline-block h-6 w-6 animate-spin rounded-full border-2 border-sky-500 border-t-transparent" />
            </div>
          ) : tickets.length === 0 ? (
            <p className="text-center py-8 text-sm text-slate-400">
              티켓 정보를 찾을 수 없습니다.
            </p>
          ) : (
            <div className="space-y-3">
              {tickets.map((tt) => {
                const isSoldOut = tt.available_quantity <= 0;
                const isSelected = selected === tt.id;
                return (
                  <button
                    key={tt.id}
                    disabled={isSoldOut}
                    onClick={() => {
                      if (isSoldOut) return;
                      setError(null);
                      setSelected(isSelected ? null : tt.id);
                    }}
                    className={`w-full text-left rounded-xl border px-4 py-4 transition-all ${
                      isSoldOut
                        ? "border-slate-100 bg-slate-50 opacity-50 cursor-not-allowed"
                        : isSelected
                          ? "border-sky-500 bg-sky-50 ring-1 ring-sky-300"
                          : "border-slate-200 bg-white hover:border-sky-300 hover:bg-sky-50 cursor-pointer"
                    }`}
                  >
                    <div className="flex items-start justify-between">
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2">
                          {/* Selection indicator */}
                          <div
                            className={`shrink-0 h-4 w-4 rounded-full border-2 flex items-center justify-center ${
                              isSelected
                                ? "border-sky-500 bg-sky-500"
                                : "border-slate-300"
                            }`}
                          >
                            {isSelected && (
                              <div className="h-1.5 w-1.5 rounded-full bg-white" />
                            )}
                          </div>
                          <p className="text-sm font-medium text-slate-900">{tt.name}</p>
                          {isSoldOut && (
                            <span className="rounded-full bg-red-50 border border-red-200 px-2 py-0.5 text-[10px] font-medium text-red-500">
                              매진
                            </span>
                          )}
                        </div>
                        {tt.description && (
                          <p className="mt-1 ml-6 text-xs text-slate-400">{tt.description}</p>
                        )}
                      </div>
                      <div className="ml-4 text-right shrink-0">
                        <p className="text-sm font-bold text-sky-600">
                          {formatPrice(tt.price)}원
                        </p>
                        <p className="text-xs text-slate-400">
                          잔여 {tt.available_quantity.toLocaleString()}석
                        </p>
                      </div>
                    </div>
                  </button>
                );
              })}
            </div>
          )}
        </div>

        {/* Order summary (sticky bottom) */}
        {selected && selectedTicket && (
          <div className="sticky bottom-4 rounded-2xl border border-sky-200 bg-white p-5 shadow-lg">
            <div className="flex items-center justify-between mb-3">
              <div>
                <p className="text-xs text-slate-400 mb-0.5">선택한 티켓</p>
                <p className="text-sm font-medium text-slate-900">
                  {selectedTicket.name}
                </p>
              </div>
              <p className="text-lg font-bold text-sky-600">
                {formatPrice(selectedTicket.price)}원
              </p>
            </div>

            {error && (
              <p className="mb-3 rounded-lg bg-red-50 border border-red-200 px-3 py-2 text-sm text-red-600">
                {error}
              </p>
            )}

            <div className="flex gap-2">
              <button
                onClick={() => { setSelected(null); setError(null); }}
                className="rounded-lg border border-slate-200 px-4 py-2.5 text-xs text-slate-500 hover:bg-slate-50 transition-colors"
              >
                취소
              </button>
              <button
                onClick={handleBook}
                disabled={booking}
                className="flex-1 rounded-lg bg-sky-500 px-5 py-2.5 text-sm font-medium text-white hover:bg-sky-600 disabled:opacity-50 transition-colors"
              >
                {booking ? (
                  <span className="flex items-center justify-center gap-2">
                    <span className="inline-block h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
                    예매 중...
                  </span>
                ) : (
                  "예매하기"
                )}
              </button>
            </div>
          </div>
        )}

        {/* Guide */}
        {!selected && tickets.length > 0 && (
          <div className="rounded-xl bg-slate-50 border border-slate-200 px-4 py-3 text-center text-xs text-slate-400">
            위에서 티켓 종류를 선택하면 예매를 진행할 수 있습니다 (1매)
          </div>
        )}
      </div>
    </AuthGuard>
  );
}
