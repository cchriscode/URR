"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { AuthGuard } from "@/components/auth-guard";
import { eventsApi, adminApi } from "@/lib/api-client";
import type { EventSummary } from "@/lib/types";

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

export default function AdminEventsPage() {
  const [events, setEvents] = useState<EventSummary[]>([]);
  const [loading, setLoading] = useState(true);

  const loadEvents = () => {
    setLoading(true);
    eventsApi
      .list({ page: 1, limit: 100 })
      .then((res) => setEvents(res.data.events ?? res.data.data ?? []))
      .catch(() => setEvents([]))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    loadEvents();
  }, []);

  const handleDelete = async (id: string) => {
    if (!confirm("정말로 이 이벤트를 삭제하시겠습니까?")) return;
    await adminApi.events.remove(id).catch(() => null);
    loadEvents();
  };

  const handleCancel = async (id: string) => {
    if (!confirm("이 이벤트를 취소하시겠습니까?")) return;
    await adminApi.events.cancel(id).catch(() => null);
    loadEvents();
  };

  const handleGenerateSeats = async (id: string) => {
    if (!confirm("이 이벤트에 좌석을 생성하시겠습니까?")) return;
    try {
      await adminApi.events.generateSeats(id);
      alert("좌석이 생성되었습니다.");
    } catch {
      alert("좌석 생성에 실패했습니다.");
    }
  };

  return (
    <AuthGuard adminOnly>
      <div className="max-w-5xl mx-auto">
        <div className="flex items-center justify-between mb-6">
          <div>
            <h1 className="text-2xl font-bold text-slate-900">이벤트 관리</h1>
            <p className="mt-1 text-sm text-slate-500">{events.length}개 이벤트</p>
          </div>
          <Link
            href="/admin/events/new"
            className="rounded-lg bg-sky-500 px-4 py-2 text-sm font-medium text-white hover:bg-sky-600 transition-colors"
          >
            + 새 이벤트
          </Link>
        </div>

        {loading ? (
          <div className="flex justify-center py-16">
            <div className="inline-block h-6 w-6 animate-spin rounded-full border-2 border-sky-500 border-t-transparent" />
          </div>
        ) : events.length === 0 ? (
          <div className="rounded-2xl border border-slate-200 bg-white p-10 text-center">
            <p className="text-slate-400 text-sm">이벤트가 없습니다</p>
          </div>
        ) : (
          <div className="rounded-xl border border-slate-200 bg-white overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-slate-100 bg-slate-50">
                  <th className="px-4 py-3 text-left font-medium text-slate-500">제목</th>
                  <th className="px-4 py-3 text-left font-medium text-slate-500">아티스트</th>
                  <th className="px-4 py-3 text-left font-medium text-slate-500">장소</th>
                  <th className="px-4 py-3 text-left font-medium text-slate-500">공연일</th>
                  <th className="px-4 py-3 text-left font-medium text-slate-500">상태</th>
                  <th className="px-4 py-3 text-right font-medium text-slate-500">관리</th>
                </tr>
              </thead>
              <tbody>
                {events.map((event) => {
                  const badge = statusBadge(event.status);
                  return (
                    <tr key={event.id} className="border-b border-slate-50 last:border-0">
                      <td className="px-4 py-3 font-medium text-slate-900 max-w-[200px] truncate">{event.title}</td>
                      <td className="px-4 py-3 text-slate-600">{event.artist_name ?? "-"}</td>
                      <td className="px-4 py-3 text-slate-600">{event.venue ?? "-"}</td>
                      <td className="px-4 py-3 text-slate-600 text-xs">{event.event_date ? new Date(event.event_date).toLocaleDateString("ko-KR") : "-"}</td>
                      <td className="px-4 py-3">
                        <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${badge.cls}`}>
                          {badge.text}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-right space-x-2">
                        <Link href={`/admin/events/edit/${event.id}`} className="text-sky-500 hover:text-sky-600 text-xs">
                          수정
                        </Link>
                        <button onClick={() => handleGenerateSeats(event.id)} className="text-amber-500 hover:text-amber-600 text-xs">
                          좌석생성
                        </button>
                        {event.status !== "cancelled" && (
                          <button onClick={() => handleCancel(event.id)} className="text-orange-400 hover:text-orange-500 text-xs">
                            취소
                          </button>
                        )}
                        <button onClick={() => handleDelete(event.id)} className="text-red-400 hover:text-red-500 text-xs">
                          삭제
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </AuthGuard>
  );
}
