"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { AuthGuard } from "@/components/auth-guard";
import { adminApi } from "@/lib/api-client";

interface AdminReservation {
  id: string;
  reservation_number?: string;
  user_name?: string;
  user_email?: string;
  event_title?: string;
  status?: string;
  total_amount?: number;
  created_at?: string;
}

function statusBadge(status?: string) {
  switch (status) {
    case "confirmed":
    case "completed":
      return { text: "확정", cls: "bg-sky-50 text-sky-600" };
    case "pending":
    case "waiting":
      return { text: "대기", cls: "bg-amber-50 text-amber-600" };
    case "cancelled":
      return { text: "취소", cls: "bg-red-50 text-red-500" };
    case "refunded":
      return { text: "환불", cls: "bg-slate-100 text-slate-500" };
    default:
      return { text: status ?? "대기", cls: "bg-slate-100 text-slate-500" };
  }
}

export default function AdminReservationsPage() {
  const [items, setItems] = useState<AdminReservation[]>([]);
  const [loading, setLoading] = useState(true);

  const loadItems = () => {
    setLoading(true);
    adminApi.reservations
      .list()
      .then((res) => setItems(res.data.reservations ?? res.data.data ?? []))
      .catch(() => setItems([]))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    loadItems();
  }, []);

  const handleStatusChange = async (id: string, newStatus: string) => {
    if (!confirm(`예매 상태를 "${newStatus}"로 변경하시겠습니까?`)) return;
    try {
      await adminApi.reservations.updateStatus(id, newStatus);
      loadItems();
    } catch {
      alert("상태 변경에 실패했습니다.");
    }
  };

  return (
    <AuthGuard adminOnly>
      <div className="max-w-5xl mx-auto">
        <div className="mb-6">
          <h1 className="text-2xl font-bold text-slate-900">예매 관리</h1>
          <p className="mt-1 text-sm text-slate-500">{items.length}건 예매</p>
        </div>

        {loading ? (
          <div className="flex justify-center py-16">
            <div className="inline-block h-6 w-6 animate-spin rounded-full border-2 border-sky-500 border-t-transparent" />
          </div>
        ) : items.length === 0 ? (
          <div className="rounded-2xl border border-slate-200 bg-white p-10 text-center">
            <p className="text-slate-400 text-sm">예매 내역이 없습니다</p>
          </div>
        ) : (
          <div className="rounded-xl border border-slate-200 bg-white overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-slate-100 bg-slate-50">
                  <th className="px-4 py-3 text-left font-medium text-slate-500">예매번호</th>
                  <th className="px-4 py-3 text-left font-medium text-slate-500">사용자</th>
                  <th className="px-4 py-3 text-left font-medium text-slate-500">이벤트</th>
                  <th className="px-4 py-3 text-left font-medium text-slate-500">상태</th>
                  <th className="px-4 py-3 text-right font-medium text-slate-500">금액</th>
                  <th className="px-4 py-3 text-right font-medium text-slate-500">관리</th>
                </tr>
              </thead>
              <tbody>
                {items.map((item) => {
                  const badge = statusBadge(item.status);
                  return (
                    <tr key={item.id} className="border-b border-slate-50 last:border-0">
                      <td className="px-4 py-3">
                        <Link href={`/reservations/${item.id}`} className="font-mono text-sky-500 hover:text-sky-600 text-xs">
                          {item.reservation_number ?? item.id.slice(0, 8)}
                        </Link>
                      </td>
                      <td className="px-4 py-3 text-slate-600 text-xs">
                        {item.user_name ?? item.user_email ?? "-"}
                      </td>
                      <td className="px-4 py-3 text-slate-700 max-w-[200px] truncate">{item.event_title ?? "-"}</td>
                      <td className="px-4 py-3">
                        <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${badge.cls}`}>
                          {badge.text}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-right text-slate-600">
                        {item.total_amount ? `${item.total_amount.toLocaleString()}원` : "-"}
                      </td>
                      <td className="px-4 py-3 text-right">
                        <select
                          className="rounded border border-slate-200 text-xs px-2 py-1 text-slate-600"
                          value=""
                          onChange={(e) => {
                            if (e.target.value) handleStatusChange(item.id, e.target.value);
                          }}
                        >
                          <option value="">상태 변경</option>
                          <option value="confirmed">확정</option>
                          <option value="cancelled">취소</option>
                          <option value="refunded">환불</option>
                        </select>
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
