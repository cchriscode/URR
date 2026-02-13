"use client";

import { useEffect, useState, useCallback } from "react";
import { useParams, useRouter } from "next/navigation";
import axios from "axios";
import { AuthGuard } from "@/components/auth-guard";
import { seatsApi } from "@/lib/api-client";
import { useQueuePolling } from "@/hooks/use-queue-polling";
import { formatPrice } from "@/lib/format";

interface Seat {
  id: string;
  seat_number?: number;
  row_number?: number;
  section?: string;
  seat_label?: string;
  status?: string;
  price?: number;
}

interface SectionData {
  name: string;
  price: number;
  rows: Map<number, Seat[]>; // row_number → seats sorted by seat_number
}

function buildSections(seats: Seat[]): SectionData[] {
  const sectionMap = new Map<string, SectionData>();

  for (const seat of seats) {
    const secName = seat.section ?? "General";
    let section = sectionMap.get(secName);
    if (!section) {
      section = { name: secName, price: seat.price ?? 0, rows: new Map() };
      sectionMap.set(secName, section);
    }
    const rowNum = seat.row_number ?? 0;
    let rowSeats = section.rows.get(rowNum);
    if (!rowSeats) {
      rowSeats = [];
      section.rows.set(rowNum, rowSeats);
    }
    rowSeats.push(seat);
  }

  // Sort seats within each row by seat_number
  for (const section of sectionMap.values()) {
    for (const [rowNum, rowSeats] of section.rows) {
      section.rows.set(
        rowNum,
        rowSeats.sort((a, b) => (a.seat_number ?? 0) - (b.seat_number ?? 0)),
      );
    }
  }

  return Array.from(sectionMap.values());
}

function seatColor(status: string | undefined, isSelected: boolean): string {
  if (isSelected) return "border-sky-500 bg-sky-500 text-white cursor-pointer ring-1 ring-sky-300";
  switch (status) {
    case "reserved":
      return "border-slate-300 bg-slate-300 text-slate-400 cursor-not-allowed";
    case "locked":
      return "border-amber-400 bg-amber-100 text-amber-600 cursor-not-allowed";
    default: // available
      return "border-slate-200 bg-slate-50 text-slate-600 hover:border-sky-400 hover:bg-sky-50 cursor-pointer";
  }
}

export default function SeatsPage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const eventId = params.id ?? "";
  const [seats, setSeats] = useState<Seat[]>([]);
  const [selected, setSelected] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [booking, setBooking] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [eventTitle, setEventTitle] = useState<string>("");
  // One-time queue guard: check if user is still queued (not continuous polling)
  const [queueChecked, setQueueChecked] = useState(false);
  const { status: queueStatus } = useQueuePolling(eventId, !queueChecked);

  useEffect(() => {
    if (!queueStatus) return;
    if (queueStatus.queued || queueStatus.status === "queued") {
      router.replace(`/queue/${eventId}`);
    } else {
      // Admitted — stop polling
      setQueueChecked(true);
    }
  }, [queueStatus, eventId, router]);

  useEffect(() => {
    if (!eventId) return;
    seatsApi
      .byEvent(eventId)
      .then(({ data }) => {
        setSeats(data.seats ?? data.data ?? []);
        if (data.event?.title) setEventTitle(data.event.title);
      })
      .catch(() => setSeats([]))
      .finally(() => setLoading(false));
  }, [eventId]);

  const toggleSeat = useCallback(
    (seatId: string, seatStatus?: string) => {
      // Can't select reserved or locked seats
      if (seatStatus === "reserved" || seatStatus === "locked") return;

      setSelected((prev) => {
        if (prev === seatId) return null; // deselect
        return seatId; // select (replaces previous)
      });
    },
    [],
  );

  const handleBook = async () => {
    if (!selected) return;
    setBooking(true);
    setError(null);
    try {
      const res = await seatsApi.reserve({
        eventId,
        seatIds: [selected],
      });
      const reservationId = res.data?.id ?? res.data?.reservation?.id;
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
          setError("이미 선택된 좌석입니다. 다른 좌석을 선택해주세요.");
          // Refresh seats to get latest status
          seatsApi.byEvent(eventId).then(({ data: d }) => {
            setSeats(d.seats ?? d.data ?? []);
          }).catch(() => {});
          setSelected(null);
        } else {
          setError(data?.message ?? data?.error ?? "예매에 실패했습니다. 다시 시도해주세요.");
        }
      } else {
        setError("네트워크 오류가 발생했습니다. 다시 시도해주세요.");
      }
      setBooking(false);
    }
  };

  const selectedSeat = seats.find((s) => s.id === selected);
  const sections = buildSections(seats);

  return (
    <AuthGuard>
      <div className="max-w-4xl mx-auto space-y-6">
        {/* Header */}
        {eventTitle && (
          <h1 className="text-xl font-bold text-slate-900">{eventTitle}</h1>
        )}

        {/* Stage + Seats */}
        <div className="rounded-2xl border border-slate-200 bg-white p-6">
          {/* Stage */}
          <div className="mb-8 text-center">
            <div className="mx-auto w-64 rounded-xl bg-gradient-to-b from-slate-700 to-slate-900 py-3 text-xs font-medium text-white tracking-widest uppercase shadow-lg">
              Stage
            </div>
            <div className="mx-auto mt-1 h-2 w-48 rounded-b-full bg-slate-100" />
          </div>

          {loading ? (
            <div className="flex justify-center py-10">
              <div className="inline-block h-6 w-6 animate-spin rounded-full border-2 border-sky-500 border-t-transparent" />
            </div>
          ) : seats.length === 0 ? (
            <p className="text-center py-10 text-sm text-slate-400">이 이벤트에 좌석이 없습니다</p>
          ) : (
            <div className="space-y-8">
              {sections.map((section) => {
                const sortedRows = Array.from(section.rows.entries()).sort(
                  ([a], [b]) => a - b,
                );
                return (
                  <div key={section.name}>
                    {/* Section header */}
                    <div className="flex items-center gap-2 mb-3 pb-2 border-b border-slate-100">
                      <span className="text-sm font-bold text-slate-800">{section.name}석</span>
                      <span className="text-xs text-slate-400">{formatPrice(section.price)}원</span>
                    </div>

                    {/* Rows */}
                    <div className="space-y-1.5">
                      {sortedRows.map(([rowNum, rowSeats]) => (
                        <div key={rowNum} className="flex items-center gap-2">
                          {/* Row label */}
                          <span className="w-10 shrink-0 text-right text-[10px] text-slate-400 font-medium">
                            {rowNum}열
                          </span>
                          {/* Seats */}
                          <div className="flex flex-wrap gap-1">
                            {rowSeats.map((seat) => {
                              const isSelected = selected === seat.id;
                              const isDisabled = seat.status === "reserved" || seat.status === "locked";
                              return (
                                <button
                                  key={seat.id}
                                  disabled={isDisabled}
                                  onClick={() => toggleSeat(seat.id, seat.status)}
                                  className={`flex h-7 w-7 items-center justify-center rounded border text-[10px] font-medium transition-all ${seatColor(seat.status, isSelected)}`}
                                  title={`${seat.seat_label ?? `${section.name}-${rowNum}-${seat.seat_number}`} (${seat.status === "reserved" ? "예매됨" : seat.status === "locked" ? "선택 중" : "선택 가능"})`}
                                >
                                  {seat.seat_number ?? ""}
                                </button>
                              );
                            })}
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                );
              })}
            </div>
          )}

          {/* Legend */}
          <div className="mt-6 flex flex-wrap justify-center gap-4 text-xs text-slate-500">
            <div className="flex items-center gap-1.5">
              <div className="h-4 w-4 rounded border border-slate-200 bg-slate-50" />
              선택 가능
            </div>
            <div className="flex items-center gap-1.5">
              <div className="h-4 w-4 rounded border border-sky-500 bg-sky-500" />
              내 선택
            </div>
            <div className="flex items-center gap-1.5">
              <div className="h-4 w-4 rounded border border-amber-400 bg-amber-100" />
              선택 중
            </div>
            <div className="flex items-center gap-1.5">
              <div className="h-4 w-4 rounded border border-slate-300 bg-slate-300" />
              예매됨
            </div>
          </div>
        </div>

        {/* Selection summary (sticky bottom) */}
        {selected && selectedSeat && (
          <div className="sticky bottom-4 rounded-2xl border border-sky-200 bg-white p-5 shadow-lg">
            <div className="flex items-center justify-between">
              <div>
                <div className="flex items-center gap-2">
                  <span className="rounded-full bg-sky-50 border border-sky-100 px-2.5 py-0.5 text-xs font-medium text-sky-700">
                    {selectedSeat.section}석
                  </span>
                  <p className="text-sm font-medium text-slate-900">
                    {selectedSeat.seat_label ?? `${selectedSeat.section}-${selectedSeat.row_number}-${selectedSeat.seat_number}`}
                  </p>
                </div>
                {selectedSeat.price != null && (
                  <p className="mt-1 text-sm font-bold text-sky-600">
                    {formatPrice(selectedSeat.price)}원
                  </p>
                )}
              </div>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => setSelected(null)}
                  className="rounded-lg border border-slate-200 px-3 py-2.5 text-xs text-slate-500 hover:bg-slate-50 transition-colors"
                >
                  취소
                </button>
                <button
                  onClick={handleBook}
                  disabled={booking}
                  className="rounded-lg bg-sky-500 px-5 py-2.5 text-sm font-medium text-white hover:bg-sky-600 disabled:opacity-50 transition-colors"
                >
                  {booking ? (
                    <span className="flex items-center gap-2">
                      <span className="inline-block h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
                      예매 중...
                    </span>
                  ) : (
                    "예매하기"
                  )}
                </button>
              </div>
            </div>
            {error && (
              <p className="mt-3 rounded-lg bg-red-50 border border-red-200 px-3 py-2 text-sm text-red-600">{error}</p>
            )}
          </div>
        )}

        {/* Info note when no selection */}
        {!selected && seats.length > 0 && (
          <div className="rounded-xl bg-slate-50 border border-slate-200 px-4 py-3 text-center text-xs text-slate-400">
            좌석을 선택하면 예매를 진행할 수 있습니다 (1석)
          </div>
        )}
      </div>
    </AuthGuard>
  );
}
