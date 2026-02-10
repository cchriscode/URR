"use client";

import { useEffect, useState, useCallback } from "react";
import { AuthGuard } from "@/components/auth-guard";
import { statsApi } from "@/lib/api-client";
import {
  ResponsiveContainer,
  BarChart,
  Bar,
  LineChart,
  Line,
  AreaChart,
  Area,
  PieChart,
  Pie,
  Cell,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
} from "recharts";

/* ───────── constants ───────── */

const COLORS = ["#0ea5e9", "#f59e0b", "#10b981", "#ef4444", "#8b5cf6", "#ec4899"];

/* ───────── tiny helpers ───────── */

function won(v: number | null | undefined): string {
  if (v == null) return "0원";
  return `${v.toLocaleString()}원`;
}

function pct(v: number | null | undefined): string {
  if (v == null) return "0.0%";
  return `${v.toFixed(1)}%`;
}

function num(v: number | null | undefined): string {
  if (v == null) return "0";
  return v.toLocaleString();
}

/* eslint-disable @typescript-eslint/no-explicit-any */

/* ───────── data extraction helpers ───────── */

function extract(res: any): any {
  return res?.data?.data ?? res?.data ?? null;
}

function extractArray(res: any, key?: string): any[] {
  const d = res?.data?.data ?? res?.data;
  if (key && d?.[key]) return d[key];
  if (Array.isArray(d)) return d;
  return [];
}

/* ───────── page ───────── */

export default function AdminStatisticsPage() {
  /* ── state for all 12 sections ── */
  const [loading, setLoading] = useState(true);
  const [realtime, setRealtime] = useState<any>(null);
  const [overview, setOverview] = useState<any>(null);
  const [conversion, setConversion] = useState<any>(null);
  const [hourly, setHourly] = useState<any>(null);
  const [daily, setDaily] = useState<any[]>([]);
  const [revenue, setRevenue] = useState<any[]>([]);
  const [cancellations, setCancellations] = useState<any>(null);
  const [seatPrefs, setSeatPrefs] = useState<any>(null);
  const [userBehavior, setUserBehavior] = useState<any>(null);
  const [performance, setPerformance] = useState<any>(null);
  const [eventStats, setEventStats] = useState<any[]>([]);
  const [payments, setPayments] = useState<any[]>([]);

  /* ── realtime fetcher (called on mount + every 30s) ── */
  const fetchRealtime = useCallback(() => {
    statsApi
      .realtime()
      .then((res) => setRealtime(extract(res)))
      .catch(() => {});
  }, []);

  /* ── initial parallel fetch ── */
  useEffect(() => {
    Promise.all([
      statsApi.realtime().then((res) => setRealtime(extract(res))).catch(() => {}),
      statsApi.overview().then((res) => setOverview(extract(res))).catch(() => {}),
      statsApi.conversion(30).then((res) => setConversion(extract(res))).catch(() => {}),
      statsApi.hourlyTraffic(7).then((res) => setHourly(extract(res))).catch(() => {}),
      statsApi.daily(30).then((res) => {
        const arr = extractArray(res, "daily");
        setDaily([...arr].reverse());
      }).catch(() => {}),
      statsApi.revenue({ period: "daily", days: 30 }).then((res) => {
        const arr = extractArray(res);
        setRevenue([...arr].reverse());
      }).catch(() => {}),
      statsApi.cancellations(30).then((res) => setCancellations(extract(res))).catch(() => {}),
      statsApi.seatPreferences().then((res) => setSeatPrefs(extract(res))).catch(() => {}),
      statsApi.userBehavior(30).then((res) => setUserBehavior(extract(res))).catch(() => {}),
      statsApi.performance().then((res) => setPerformance(extract(res))).catch(() => {}),
      statsApi.events({ limit: 20, sortBy: "revenue" }).then((res) => {
        const d = extract(res);
        setEventStats(d?.events ?? (Array.isArray(d) ? d : []));
      }).catch(() => {}),
      statsApi.payments().then((res) => setPayments(extractArray(res))).catch(() => {}),
    ]).finally(() => setLoading(false));
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  /* ── 30-second realtime refresh ── */
  useEffect(() => {
    const id = setInterval(fetchRealtime, 30_000);
    return () => clearInterval(id);
  }, [fetchRealtime]);

  /* ── derived data ── */
  const funnel = conversion?.funnel;
  const rates = conversion?.rates;
  const hourlyData: any[] = hourly?.hourly ?? [];
  const peakHour = hourly?.peakHour;
  const cancelOverview = cancellations?.overview;
  const cancelByEvent: any[] = cancellations?.byEvent ?? [];
  const seatSections: any[] = seatPrefs?.bySection ?? [];
  const priceTiers: any[] = seatPrefs?.byPriceTier ?? [];
  const userTypes: any[] = userBehavior?.userTypes ?? [];
  const spendingDist: any[] = userBehavior?.spendingDistribution ?? [];
  const avgMetrics = userBehavior?.averageMetrics;

  /* ── funnel total for proportional bar ── */
  const funnelTotal = (funnel?.total_started ?? 0) || 1;

  return (
    <AuthGuard adminOnly>
      <div className="max-w-6xl mx-auto">
        {/* Header */}
        <div className="mb-8">
          <h1 className="text-2xl font-bold text-slate-900">통계 대시보드</h1>
          <p className="mt-1 text-sm text-slate-500">
            플랫폼 전체 현황 및 심층 분석
          </p>
        </div>

        {loading ? (
          <div className="flex justify-center py-16">
            <div className="inline-block h-6 w-6 animate-spin rounded-full border-2 border-sky-500 border-t-transparent" />
          </div>
        ) : (
          <div className="space-y-8">
            {/* ═══════════════════════════════════════════
                1. 실시간 현황 (Realtime Status)
            ═══════════════════════════════════════════ */}
            <section>
              <div className="flex items-center gap-2 mb-4">
                <span className="relative flex h-2.5 w-2.5">
                  <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-emerald-400 opacity-75" />
                  <span className="relative inline-flex h-2.5 w-2.5 rounded-full bg-emerald-500" />
                </span>
                <h2 className="text-sm font-medium text-slate-700">
                  실시간 현황
                </h2>
                <span className="rounded bg-emerald-100 px-1.5 py-0.5 text-[10px] font-bold uppercase tracking-wider text-emerald-700">
                  LIVE
                </span>
              </div>

              <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
                <div className="rounded-xl border border-slate-200 bg-white p-5">
                  <p className="text-xs font-medium uppercase tracking-wider text-slate-400">
                    잠긴 좌석
                  </p>
                  <p className="mt-2 text-2xl font-bold text-sky-600">
                    {num(realtime?.current?.locked_seats)}
                  </p>
                </div>
                <div className="rounded-xl border border-slate-200 bg-white p-5">
                  <p className="text-xs font-medium uppercase tracking-wider text-slate-400">
                    활성 결제
                  </p>
                  <p className="mt-2 text-2xl font-bold text-amber-500">
                    {num(realtime?.current?.active_payments)}
                  </p>
                </div>
                <div className="rounded-xl border border-slate-200 bg-white p-5">
                  <p className="text-xs font-medium uppercase tracking-wider text-slate-400">
                    활성 사용자
                  </p>
                  <p className="mt-2 text-2xl font-bold text-slate-900">
                    {num(realtime?.current?.active_users)}
                  </p>
                </div>
                <div className="rounded-xl border border-slate-200 bg-white p-5">
                  <p className="text-xs font-medium uppercase tracking-wider text-slate-400">
                    최근 1시간 매출
                  </p>
                  <p className="mt-2 text-2xl font-bold text-emerald-500">
                    {won(realtime?.lastHour?.revenue)}
                  </p>
                </div>
              </div>

              {/* Trending events */}
              {(realtime?.trendingEvents?.length ?? 0) > 0 && (
                <div className="mt-4 rounded-xl border border-slate-200 bg-white p-5">
                  <p className="text-xs font-medium uppercase tracking-wider text-slate-400 mb-3">
                    인기 이벤트
                  </p>
                  <ul className="space-y-2">
                    {realtime.trendingEvents.map((ev: any, i: number) => (
                      <li
                        key={ev?.event_id ?? i}
                        className="flex items-center justify-between text-sm"
                      >
                        <span className="text-slate-700 truncate max-w-[70%]">
                          {ev?.title ?? `이벤트 ${i + 1}`}
                        </span>
                        <span className="text-sky-600 font-medium">
                          {num(ev?.reservations ?? ev?.count)} 건
                        </span>
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </section>

            {/* ═══════════════════════════════════════════
                2. 개요 (Overview)
            ═══════════════════════════════════════════ */}
            <section>
              <h2 className="text-sm font-medium text-slate-700 mb-4">개요</h2>
              <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
                {[
                  {
                    label: "총 사용자",
                    value: num(overview?.total_users),
                    icon: "\u{1F465}",
                    color: "text-sky-600",
                  },
                  {
                    label: "진행 중 이벤트",
                    value: num(overview?.active_events),
                    icon: "\u{1F3AB}",
                    color: "text-sky-600",
                  },
                  {
                    label: "확정 예매",
                    value: num(overview?.confirmed_reservations),
                    icon: "\u2714",
                    color: "text-emerald-500",
                  },
                  {
                    label: "총 매출",
                    value: won(overview?.total_revenue),
                    icon: "\u{1F4B0}",
                    color: "text-amber-500",
                  },
                ].map((c) => (
                  <div
                    key={c.label}
                    className="rounded-xl border border-slate-200 bg-white p-5"
                  >
                    <div className="flex items-center justify-between">
                      <p className="text-xs font-medium uppercase tracking-wider text-slate-400">
                        {c.label}
                      </p>
                      <span className="text-lg">{c.icon}</span>
                    </div>
                    <p className={`mt-2 text-2xl font-bold ${c.color}`}>
                      {c.value}
                    </p>
                  </div>
                ))}
              </div>
            </section>

            {/* ═══════════════════════════════════════════
                3. 전환 퍼널 (Conversion Funnel)
            ═══════════════════════════════════════════ */}
            <section>
              <h2 className="text-sm font-medium text-slate-700 mb-4">
                전환 퍼널
              </h2>
              <div className="rounded-xl border border-slate-200 bg-white p-5">
                {/* Proportional horizontal bar */}
                <div className="mb-4">
                  <div className="flex h-10 w-full overflow-hidden rounded-lg">
                    <div
                      className="flex items-center justify-center bg-sky-500 text-xs font-medium text-white transition-all"
                      style={{
                        width: `${((funnel?.completed ?? 0) / funnelTotal) * 100}%`,
                        minWidth:
                          (funnel?.completed ?? 0) > 0 ? "40px" : undefined,
                      }}
                    >
                      {funnel?.completed ?? 0}
                    </div>
                    <div
                      className="flex items-center justify-center bg-amber-400 text-xs font-medium text-white transition-all"
                      style={{
                        width: `${((funnel?.pending ?? 0) / funnelTotal) * 100}%`,
                        minWidth:
                          (funnel?.pending ?? 0) > 0 ? "40px" : undefined,
                      }}
                    >
                      {funnel?.pending ?? 0}
                    </div>
                    <div
                      className="flex items-center justify-center bg-red-400 text-xs font-medium text-white transition-all"
                      style={{
                        width: `${((funnel?.cancelled ?? 0) / funnelTotal) * 100}%`,
                        minWidth:
                          (funnel?.cancelled ?? 0) > 0 ? "40px" : undefined,
                      }}
                    >
                      {funnel?.cancelled ?? 0}
                    </div>
                  </div>
                  <div className="mt-2 flex gap-4 text-xs text-slate-500">
                    <span className="flex items-center gap-1">
                      <span className="inline-block h-2.5 w-2.5 rounded-sm bg-sky-500" />
                      완료
                    </span>
                    <span className="flex items-center gap-1">
                      <span className="inline-block h-2.5 w-2.5 rounded-sm bg-amber-400" />
                      대기
                    </span>
                    <span className="flex items-center gap-1">
                      <span className="inline-block h-2.5 w-2.5 rounded-sm bg-red-400" />
                      취소
                    </span>
                    <span className="ml-auto text-slate-400">
                      전체 시작: {num(funnel?.total_started)}
                    </span>
                  </div>
                </div>

                {/* Rate cards */}
                <div className="grid gap-4 sm:grid-cols-3">
                  <div className="rounded-lg border border-slate-100 p-4 text-center">
                    <p className="text-xs text-slate-400 mb-1">전환율</p>
                    <p className="text-xl font-bold text-sky-600">
                      {pct(rates?.conversion_rate)}
                    </p>
                  </div>
                  <div className="rounded-lg border border-slate-100 p-4 text-center">
                    <p className="text-xs text-slate-400 mb-1">취소율</p>
                    <p className="text-xl font-bold text-red-500">
                      {pct(rates?.cancellation_rate)}
                    </p>
                  </div>
                  <div className="rounded-lg border border-slate-100 p-4 text-center">
                    <p className="text-xs text-slate-400 mb-1">대기율</p>
                    <p className="text-xl font-bold text-amber-500">
                      {pct(rates?.pending_rate)}
                    </p>
                  </div>
                </div>
              </div>
            </section>

            {/* ═══════════════════════════════════════════
                4. 시간대별 트래픽 (Hourly Traffic)
            ═══════════════════════════════════════════ */}
            <section>
              <h2 className="text-sm font-medium text-slate-700 mb-4">
                시간대별 트래픽
              </h2>
              <div className="rounded-xl border border-slate-200 bg-white p-5">
                {hourlyData.length > 0 ? (
                  <ResponsiveContainer width="100%" height={300}>
                    <BarChart data={hourlyData}>
                      <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                      <XAxis
                        dataKey="hour"
                        tick={{ fontSize: 12, fill: "#94a3b8" }}
                        tickFormatter={(h: number) => `${h}시`}
                      />
                      <YAxis tick={{ fontSize: 12, fill: "#94a3b8" }} />
                      <Tooltip
                        formatter={(v: any) => [`${v ?? 0}건`, "예매"]}
                        labelFormatter={(h: any) => `${h}시`}
                      />
                      <Bar
                        dataKey="total_reservations"
                        name="예매"
                        fill="#0ea5e9"
                        radius={[4, 4, 0, 0]}
                      />
                    </BarChart>
                  </ResponsiveContainer>
                ) : (
                  <p className="py-12 text-center text-sm text-slate-400">
                    데이터가 없습니다
                  </p>
                )}

                {peakHour != null && (
                  <div className="mt-4 rounded-lg border border-slate-100 p-4 text-center">
                    <p className="text-xs text-slate-400 mb-1">피크 시간대</p>
                    <p className="text-lg font-bold text-sky-600">
                      {peakHour?.hour ?? "-"}시{" "}
                      <span className="text-sm font-normal text-slate-500">
                        ({num(peakHour?.reservations)}건)
                      </span>
                    </p>
                  </div>
                )}
              </div>
            </section>

            {/* ═══════════════════════════════════════════
                5. 일별 예매 추이 (Daily Reservations)
            ═══════════════════════════════════════════ */}
            <section>
              <h2 className="text-sm font-medium text-slate-700 mb-4">
                일별 예매 추이 (30일)
              </h2>
              <div className="rounded-xl border border-slate-200 bg-white p-5">
                {daily.length > 0 ? (
                  <ResponsiveContainer width="100%" height={300}>
                    <LineChart data={daily}>
                      <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                      <XAxis
                        dataKey="date"
                        tick={{ fontSize: 11, fill: "#94a3b8" }}
                        tickFormatter={(d: string) => {
                          const parts = d?.split?.("-");
                          return parts
                            ? `${parts[1]}/${parts[2]}`
                            : d;
                        }}
                      />
                      <YAxis tick={{ fontSize: 12, fill: "#94a3b8" }} />
                      <Tooltip
                        labelFormatter={(d: any) => `${d}`}
                        formatter={(v: any, name: any) => [
                          `${v ?? 0}건`,
                          name === "reservations" ? "예매" : "확정",
                        ]}
                      />
                      <Legend
                        formatter={(value: string) =>
                          value === "reservations" ? "예매" : "확정"
                        }
                      />
                      <Line
                        type="monotone"
                        dataKey="reservations"
                        stroke="#0ea5e9"
                        strokeWidth={2}
                        dot={false}
                        name="reservations"
                      />
                      <Line
                        type="monotone"
                        dataKey="confirmed"
                        stroke="#10b981"
                        strokeWidth={2}
                        dot={false}
                        name="confirmed"
                      />
                    </LineChart>
                  </ResponsiveContainer>
                ) : (
                  <p className="py-12 text-center text-sm text-slate-400">
                    데이터가 없습니다
                  </p>
                )}
              </div>
            </section>

            {/* ═══════════════════════════════════════════
                6. 일별 매출 추이 (Daily Revenue)
            ═══════════════════════════════════════════ */}
            <section>
              <h2 className="text-sm font-medium text-slate-700 mb-4">
                일별 매출 추이 (30일)
              </h2>
              <div className="rounded-xl border border-slate-200 bg-white p-5">
                {revenue.length > 0 ? (
                  <ResponsiveContainer width="100%" height={300}>
                    <AreaChart data={revenue}>
                      <defs>
                        <linearGradient
                          id="revenueGradient"
                          x1="0"
                          y1="0"
                          x2="0"
                          y2="1"
                        >
                          <stop
                            offset="5%"
                            stopColor="#f59e0b"
                            stopOpacity={0.3}
                          />
                          <stop
                            offset="95%"
                            stopColor="#f59e0b"
                            stopOpacity={0}
                          />
                        </linearGradient>
                      </defs>
                      <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                      <XAxis
                        dataKey="period"
                        tick={{ fontSize: 11, fill: "#94a3b8" }}
                        tickFormatter={(d: string) => {
                          const parts = d?.split?.("-");
                          return parts && parts.length >= 3
                            ? `${parts[1]}/${parts[2]}`
                            : d;
                        }}
                      />
                      <YAxis
                        tick={{ fontSize: 12, fill: "#94a3b8" }}
                        tickFormatter={(v: number) =>
                          v >= 10000
                            ? `${(v / 10000).toFixed(0)}만`
                            : v.toLocaleString()
                        }
                      />
                      <Tooltip
                        formatter={(v: any) => [won(v), "매출"]}
                        labelFormatter={(d: any) => `${d}`}
                      />
                      <Area
                        type="monotone"
                        dataKey="total_revenue"
                        stroke="#f59e0b"
                        strokeWidth={2}
                        fill="url(#revenueGradient)"
                        name="매출"
                      />
                    </AreaChart>
                  </ResponsiveContainer>
                ) : (
                  <p className="py-12 text-center text-sm text-slate-400">
                    데이터가 없습니다
                  </p>
                )}
              </div>
            </section>

            {/* ═══════════════════════════════════════════
                7. 취소/환불 분석 (Cancellation Analysis)
            ═══════════════════════════════════════════ */}
            <section>
              <h2 className="text-sm font-medium text-slate-700 mb-4">
                취소/환불 분석
              </h2>

              {/* Overview cards */}
              <div className="grid gap-4 sm:grid-cols-3 mb-4">
                <div className="rounded-xl border border-slate-200 bg-white p-5">
                  <p className="text-xs font-medium uppercase tracking-wider text-slate-400">
                    총 취소
                  </p>
                  <p className="mt-2 text-2xl font-bold text-red-500">
                    {num(cancelOverview?.total_cancelled)}
                  </p>
                </div>
                <div className="rounded-xl border border-slate-200 bg-white p-5">
                  <p className="text-xs font-medium uppercase tracking-wider text-slate-400">
                    취소까지 평균 시간
                  </p>
                  <p className="mt-2 text-2xl font-bold text-slate-900">
                    {cancelOverview?.avg_hours_before_cancel != null
                      ? `${Number(cancelOverview.avg_hours_before_cancel).toFixed(1)}시간`
                      : "-"}
                  </p>
                </div>
                <div className="rounded-xl border border-slate-200 bg-white p-5">
                  <p className="text-xs font-medium uppercase tracking-wider text-slate-400">
                    총 환불 금액
                  </p>
                  <p className="mt-2 text-2xl font-bold text-amber-500">
                    {won(cancelOverview?.total_refund_amount)}
                  </p>
                </div>
              </div>

              {/* Event breakdown table */}
              {cancelByEvent.length > 0 && (
                <div className="rounded-xl border border-slate-200 bg-white p-5">
                  <p className="text-xs font-medium uppercase tracking-wider text-slate-400 mb-3">
                    이벤트별 취소 현황
                  </p>
                  <div className="overflow-x-auto">
                    <table className="w-full text-sm">
                      <thead>
                        <tr className="border-b border-slate-100">
                          <th className="px-3 py-2 text-left text-xs font-medium text-slate-400">
                            이벤트
                          </th>
                          <th className="px-3 py-2 text-right text-xs font-medium text-slate-400">
                            취소 수
                          </th>
                          <th className="px-3 py-2 text-right text-xs font-medium text-slate-400">
                            환불 금액
                          </th>
                        </tr>
                      </thead>
                      <tbody>
                        {cancelByEvent.map((ev: any, i: number) => (
                          <tr
                            key={ev?.event_id ?? i}
                            className="border-b border-slate-50 last:border-0"
                          >
                            <td className="px-3 py-2 text-slate-700 truncate max-w-[200px]">
                              {ev?.title ?? ev?.event_title ?? "-"}
                            </td>
                            <td className="px-3 py-2 text-right font-medium text-red-500">
                              {num(ev?.cancelled_count ?? ev?.total_cancelled)}
                            </td>
                            <td className="px-3 py-2 text-right text-amber-600">
                              {won(ev?.refund_amount ?? ev?.total_refund)}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              )}
            </section>

            {/* ═══════════════════════════════════════════
                8. 좌석 선호도 (Seat Preferences)
            ═══════════════════════════════════════════ */}
            <section>
              <h2 className="text-sm font-medium text-slate-700 mb-4">
                좌석 선호도
              </h2>
              <div className="grid gap-4 lg:grid-cols-2">
                {/* Section PieChart */}
                <div className="rounded-xl border border-slate-200 bg-white p-5">
                  <p className="text-xs font-medium uppercase tracking-wider text-slate-400 mb-3">
                    구역별 예매 비율
                  </p>
                  {seatSections.length > 0 ? (
                    <ResponsiveContainer width="100%" height={300}>
                      <PieChart>
                        <Pie
                          data={seatSections}
                          dataKey="reserved_seats"
                          nameKey="section"
                          cx="50%"
                          cy="50%"
                          outerRadius={100}
                          label={({ section, percent }: any) =>
                            `${section} ${(percent * 100).toFixed(0)}%`
                          }
                        >
                          {seatSections.map((_: any, idx: number) => (
                            <Cell
                              key={idx}
                              fill={COLORS[idx % COLORS.length]}
                            />
                          ))}
                        </Pie>
                        <Tooltip
                          formatter={(v: any, _: any, entry: any) => [
                            `${v ?? 0}석 / ${entry?.payload?.total_seats ?? "-"}석`,
                            entry?.payload?.section,
                          ]}
                        />
                      </PieChart>
                    </ResponsiveContainer>
                  ) : (
                    <p className="py-12 text-center text-sm text-slate-400">
                      데이터가 없습니다
                    </p>
                  )}
                </div>

                {/* Price tier horizontal bars */}
                <div className="rounded-xl border border-slate-200 bg-white p-5">
                  <p className="text-xs font-medium uppercase tracking-wider text-slate-400 mb-3">
                    가격대별 선호도
                  </p>
                  {priceTiers.length > 0 ? (
                    <div className="space-y-3 py-4">
                      {priceTiers.map((tier: any, idx: number) => {
                        const maxCount = Math.max(
                          ...priceTiers.map(
                            (t: any) =>
                              t?.reserved_seats ?? t?.count ?? t?.reservations ?? 0
                          ),
                          1
                        );
                        const count =
                          tier?.reserved_seats ?? tier?.count ?? tier?.reservations ?? 0;
                        const widthPct = (count / maxCount) * 100;
                        return (
                          <div key={tier?.tier ?? tier?.price_tier ?? idx}>
                            <div className="flex items-center justify-between text-sm mb-1">
                              <span className="text-slate-600">
                                {tier?.tier ?? tier?.price_tier ?? `구간 ${idx + 1}`}
                              </span>
                              <span className="font-medium text-slate-900">
                                {num(count)}석
                              </span>
                            </div>
                            <div className="h-3 w-full overflow-hidden rounded-full bg-slate-100">
                              <div
                                className="h-full rounded-full transition-all"
                                style={{
                                  width: `${widthPct}%`,
                                  backgroundColor:
                                    COLORS[idx % COLORS.length],
                                }}
                              />
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  ) : (
                    <p className="py-12 text-center text-sm text-slate-400">
                      데이터가 없습니다
                    </p>
                  )}
                </div>
              </div>
            </section>

            {/* ═══════════════════════════════════════════
                9. 사용자 행동 (User Behavior)
            ═══════════════════════════════════════════ */}
            <section>
              <h2 className="text-sm font-medium text-slate-700 mb-4">
                사용자 행동
              </h2>
              <div className="grid gap-4 lg:grid-cols-2">
                {/* User type PieChart */}
                <div className="rounded-xl border border-slate-200 bg-white p-5">
                  <p className="text-xs font-medium uppercase tracking-wider text-slate-400 mb-3">
                    사용자 유형
                  </p>
                  {userTypes.length > 0 ? (
                    <ResponsiveContainer width="100%" height={300}>
                      <PieChart>
                        <Pie
                          data={userTypes}
                          dataKey="user_count"
                          nameKey="user_type"
                          cx="50%"
                          cy="50%"
                          outerRadius={100}
                          label={({ user_type, percent }: any) => {
                            const labelMap: Record<string, string> = {
                              new: "신규",
                              returning: "재방문",
                              loyal: "충성",
                            };
                            return `${labelMap[user_type] ?? user_type} ${(percent * 100).toFixed(0)}%`;
                          }}
                        >
                          {userTypes.map((_: any, idx: number) => (
                            <Cell
                              key={idx}
                              fill={COLORS[idx % COLORS.length]}
                            />
                          ))}
                        </Pie>
                        <Tooltip
                          formatter={(v: any, _: any, entry: any) => {
                            const labelMap: Record<string, string> = {
                              new: "신규",
                              returning: "재방문",
                              loyal: "충성",
                            };
                            const raw = entry?.payload?.user_type ?? "";
                            return [
                              `${v ?? 0}명`,
                              labelMap[raw] ?? raw,
                            ];
                          }}
                        />
                      </PieChart>
                    </ResponsiveContainer>
                  ) : (
                    <p className="py-12 text-center text-sm text-slate-400">
                      데이터가 없습니다
                    </p>
                  )}
                </div>

                {/* Spending distribution + average metrics */}
                <div className="space-y-4">
                  {/* Spending tiers */}
                  <div className="rounded-xl border border-slate-200 bg-white p-5">
                    <p className="text-xs font-medium uppercase tracking-wider text-slate-400 mb-3">
                      지출 분포
                    </p>
                    {spendingDist.length > 0 ? (
                      <div className="space-y-2">
                        {spendingDist.map((tier: any, idx: number) => {
                          const maxVal = Math.max(
                            ...spendingDist.map(
                              (t: any) => t?.user_count ?? t?.count ?? 0
                            ),
                            1
                          );
                          const val = tier?.user_count ?? tier?.count ?? 0;
                          return (
                            <div key={tier?.range ?? tier?.tier ?? idx}>
                              <div className="flex items-center justify-between text-xs mb-0.5">
                                <span className="text-slate-500">
                                  {tier?.range ?? tier?.tier ?? `구간 ${idx + 1}`}
                                </span>
                                <span className="font-medium text-slate-700">
                                  {num(val)}명
                                </span>
                              </div>
                              <div className="h-2 w-full overflow-hidden rounded-full bg-slate-100">
                                <div
                                  className="h-full rounded-full"
                                  style={{
                                    width: `${(val / maxVal) * 100}%`,
                                    backgroundColor:
                                      COLORS[idx % COLORS.length],
                                  }}
                                />
                              </div>
                            </div>
                          );
                        })}
                      </div>
                    ) : (
                      <p className="py-6 text-center text-sm text-slate-400">
                        데이터가 없습니다
                      </p>
                    )}
                  </div>

                  {/* Average metrics */}
                  {avgMetrics && (
                    <div className="grid gap-4 grid-cols-2">
                      <div className="rounded-xl border border-slate-200 bg-white p-5 text-center">
                        <p className="text-xs text-slate-400 mb-1">
                          평균 예매 수
                        </p>
                        <p className="text-xl font-bold text-sky-600">
                          {avgMetrics?.avg_reservations != null
                            ? Number(avgMetrics.avg_reservations).toFixed(1)
                            : "-"}
                        </p>
                      </div>
                      <div className="rounded-xl border border-slate-200 bg-white p-5 text-center">
                        <p className="text-xs text-slate-400 mb-1">
                          평균 지출
                        </p>
                        <p className="text-xl font-bold text-amber-500">
                          {avgMetrics?.avg_spending != null
                            ? won(Number(avgMetrics.avg_spending))
                            : "-"}
                        </p>
                      </div>
                    </div>
                  )}
                </div>
              </div>
            </section>

            {/* ═══════════════════════════════════════════
                10. 시스템 성능 (System Performance)
            ═══════════════════════════════════════════ */}
            <section>
              <h2 className="text-sm font-medium text-slate-700 mb-4">
                시스템 성능
              </h2>
              <div className="grid gap-4 sm:grid-cols-3">
                <div className="rounded-xl border border-slate-200 bg-white p-5">
                  <p className="text-xs font-medium uppercase tracking-wider text-slate-400">
                    데이터베이스 크기
                  </p>
                  <p className="mt-2 text-2xl font-bold text-slate-900">
                    {performance?.database?.size ?? "-"}
                  </p>
                </div>
                <div className="rounded-xl border border-slate-200 bg-white p-5">
                  <p className="text-xs font-medium uppercase tracking-wider text-slate-400">
                    성공률
                  </p>
                  <p className="mt-2 text-2xl font-bold text-emerald-500">
                    {performance?.recentPerformance?.successRate != null
                      ? pct(Number(performance.recentPerformance.successRate))
                      : "-"}
                  </p>
                </div>
                <div className="rounded-xl border border-slate-200 bg-white p-5">
                  <p className="text-xs font-medium uppercase tracking-wider text-slate-400">
                    테이블 수
                  </p>
                  <p className="mt-2 text-2xl font-bold text-sky-600">
                    {performance?.database?.tableCounts != null
                      ? num(
                          typeof performance.database.tableCounts === "number"
                            ? performance.database.tableCounts
                            : Object.keys(performance.database.tableCounts).length
                        )
                      : "-"}
                  </p>
                  {performance?.database?.tableCounts &&
                    typeof performance.database.tableCounts === "object" && (
                      <div className="mt-3 space-y-1">
                        {Object.entries(
                          performance.database.tableCounts as Record<
                            string,
                            number
                          >
                        ).map(([table, count]) => (
                          <div
                            key={table}
                            className="flex items-center justify-between text-xs"
                          >
                            <span className="text-slate-500 truncate max-w-[60%]">
                              {table}
                            </span>
                            <span className="font-medium text-slate-700">
                              {num(count as number)}
                            </span>
                          </div>
                        ))}
                      </div>
                    )}
                </div>
              </div>
            </section>

            {/* ═══════════════════════════════════════════
                11. 이벤트별 통계 (Event Stats Table)
            ═══════════════════════════════════════════ */}
            <section>
              <h2 className="text-sm font-medium text-slate-700 mb-4">
                이벤트별 통계
              </h2>
              <div className="rounded-xl border border-slate-200 bg-white p-5">
                {eventStats.length > 0 ? (
                  <div className="overflow-x-auto">
                    <table className="w-full text-sm">
                      <thead>
                        <tr className="border-b border-slate-100">
                          <th className="px-3 py-2 text-left text-xs font-medium text-slate-400">
                            #
                          </th>
                          <th className="px-3 py-2 text-left text-xs font-medium text-slate-400">
                            이벤트
                          </th>
                          <th className="px-3 py-2 text-right text-xs font-medium text-slate-400">
                            예매 수
                          </th>
                          <th className="px-3 py-2 text-right text-xs font-medium text-slate-400">
                            매출
                          </th>
                          <th className="px-3 py-2 text-right text-xs font-medium text-slate-400">
                            좌석 활용률
                          </th>
                        </tr>
                      </thead>
                      <tbody>
                        {eventStats.map((ev: any, i: number) => (
                          <tr
                            key={ev?.event_id ?? i}
                            className="border-b border-slate-50 last:border-0"
                          >
                            <td className="px-3 py-2 text-slate-400 font-medium">
                              {i + 1}
                            </td>
                            <td className="px-3 py-2 text-slate-900 font-medium truncate max-w-[250px]">
                              {ev?.title ?? "-"}
                            </td>
                            <td className="px-3 py-2 text-right text-slate-600">
                              {num(ev?.total_reservations)}
                            </td>
                            <td className="px-3 py-2 text-right text-amber-600 font-medium">
                              {won(ev?.total_revenue)}
                            </td>
                            <td className="px-3 py-2 text-right text-sky-600">
                              {ev?.seat_utilization != null
                                ? pct(Number(ev.seat_utilization))
                                : "-"}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                ) : (
                  <p className="py-12 text-center text-sm text-slate-400">
                    데이터가 없습니다
                  </p>
                )}
              </div>
            </section>

            {/* ═══════════════════════════════════════════
                12. 결제 수단 (Payment Methods)
            ═══════════════════════════════════════════ */}
            <section>
              <h2 className="text-sm font-medium text-slate-700 mb-4">
                결제 수단
              </h2>
              <div className="grid gap-4 lg:grid-cols-2">
                {/* PieChart */}
                <div className="rounded-xl border border-slate-200 bg-white p-5">
                  {payments.length > 0 ? (
                    <ResponsiveContainer width="100%" height={300}>
                      <PieChart>
                        <Pie
                          data={payments}
                          dataKey="count"
                          nameKey="method"
                          cx="50%"
                          cy="50%"
                          outerRadius={100}
                          label={({ method, percent }: any) =>
                            `${method} ${(percent * 100).toFixed(0)}%`
                          }
                        >
                          {payments.map((_: any, idx: number) => (
                            <Cell
                              key={idx}
                              fill={COLORS[idx % COLORS.length]}
                            />
                          ))}
                        </Pie>
                        <Tooltip
                          formatter={(v: any, _: any, entry: any) => [
                            `${v ?? 0}건`,
                            entry?.payload?.method,
                          ]}
                        />
                      </PieChart>
                    </ResponsiveContainer>
                  ) : (
                    <p className="py-12 text-center text-sm text-slate-400">
                      데이터가 없습니다
                    </p>
                  )}
                </div>

                {/* Summary table */}
                <div className="rounded-xl border border-slate-200 bg-white p-5">
                  <p className="text-xs font-medium uppercase tracking-wider text-slate-400 mb-3">
                    결제 수단별 상세
                  </p>
                  {payments.length > 0 ? (
                    <div className="overflow-x-auto">
                      <table className="w-full text-sm">
                        <thead>
                          <tr className="border-b border-slate-100">
                            <th className="px-3 py-2 text-left text-xs font-medium text-slate-400">
                              수단
                            </th>
                            <th className="px-3 py-2 text-right text-xs font-medium text-slate-400">
                              건수
                            </th>
                            <th className="px-3 py-2 text-right text-xs font-medium text-slate-400">
                              총 금액
                            </th>
                            <th className="px-3 py-2 text-right text-xs font-medium text-slate-400">
                              평균 금액
                            </th>
                          </tr>
                        </thead>
                        <tbody>
                          {payments.map((p: any, idx: number) => (
                            <tr
                              key={p?.method ?? idx}
                              className="border-b border-slate-50 last:border-0"
                            >
                              <td className="px-3 py-2 text-slate-700 font-medium">
                                <span className="flex items-center gap-2">
                                  <span
                                    className="inline-block h-2.5 w-2.5 rounded-full"
                                    style={{
                                      backgroundColor:
                                        COLORS[idx % COLORS.length],
                                    }}
                                  />
                                  {p?.method ?? "-"}
                                </span>
                              </td>
                              <td className="px-3 py-2 text-right text-slate-600">
                                {num(p?.count)}
                              </td>
                              <td className="px-3 py-2 text-right text-amber-600 font-medium">
                                {won(p?.total_amount)}
                              </td>
                              <td className="px-3 py-2 text-right text-slate-500">
                                {won(p?.average_amount)}
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  ) : (
                    <p className="py-12 text-center text-sm text-slate-400">
                      데이터가 없습니다
                    </p>
                  )}
                </div>
              </div>
            </section>
          </div>
        )}
      </div>
    </AuthGuard>
  );
}
