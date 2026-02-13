"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { AuthGuard } from "@/components/auth-guard";
import { adminApi } from "@/lib/api-client";

export default function AdminPage() {
  const [stats, setStats] = useState<Record<string, number | string>>({});

  useEffect(() => {
    adminApi.dashboard().then((res) => {
      const data = res.data?.stats ?? res.data?.data ?? res.data ?? {};
      setStats(data);
    }).catch(() => {});
  }, []);

  const cards = [
    {
      href: "/admin/events",
      label: "이벤트 관리",
      icon: "\uD83C\uDFAB",
      description: "이벤트 생성, 수정, 좌석 및 티켓 관리",
      stat: stats.totalEvents,
      statLabel: "개 이벤트",
    },
    {
      href: "/admin/reservations",
      label: "예매 관리",
      icon: "\uD83C\uDF9F\uFE0F",
      description: "전체 예매 조회 및 상태 관리",
      stat: stats.totalReservations,
      statLabel: "건 예매",
    },
    {
      href: "/admin/statistics",
      label: "통계",
      icon: "\uD83D\uDCC8",
      description: "매출, 예매 현황, 사용자 분석",
      stat: stats.totalRevenue,
      statLabel: "원 매출",
    },
  ];

  return (
    <AuthGuard adminOnly>
      <div className="max-w-4xl mx-auto">
        <div className="mb-8">
          <h1 className="text-2xl font-bold text-slate-900">관리자 대시보드</h1>
          <p className="mt-1 text-sm text-slate-500">URR 플랫폼 관리</p>
        </div>

        <div className="grid gap-4 sm:grid-cols-3">
          {cards.map((card) => (
            <Link
              key={card.href}
              href={card.href}
              className="group rounded-xl border border-slate-200 bg-white p-5 transition-all hover:border-sky-300 hover:shadow-sm"
            >
              <div className="text-2xl mb-3">{card.icon}</div>
              <p className="font-semibold text-slate-900 group-hover:text-sky-600 transition-colors">{card.label}</p>
              <p className="mt-1 text-xs text-slate-500">{card.description}</p>
              {card.stat !== undefined ? (
                <p className="mt-3 text-lg font-bold text-sky-500">
                  {typeof card.stat === "number" ? card.stat.toLocaleString() : card.stat}
                  <span className="text-xs font-normal text-slate-400 ml-1">{card.statLabel}</span>
                </p>
              ) : null}
            </Link>
          ))}
        </div>
      </div>
    </AuthGuard>
  );
}
