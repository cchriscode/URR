"use client";

import { useEffect, useState, useCallback } from "react";
import dynamic from "next/dynamic";
import { AuthGuard } from "@/components/auth-guard";
import { Spinner } from "@/components/ui/Spinner";
import { statsApi } from "@/lib/api-client";
import type { AxiosResponse } from "axios";
import type {
  HourlyDataPoint,
  DailyDataPoint,
  RevenueDataPoint,
  SeatSection,
  PriceTier,
  UserType,
  SpendingTier,
  PaymentMethod,
  PeakHour,
} from "./StatisticsCharts";

const StatisticsCharts = dynamic(() => import("./StatisticsCharts"), {
  ssr: false,
  loading: () => (
    <div className="h-64 flex items-center justify-center">
      <Spinner />
    </div>
  ),
});

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

/* ───────── interfaces ───────── */

interface RealtimeCurrent {
  locked_seats?: number;
  active_payments?: number;
  active_users?: number;
}

interface RealtimeLastHour {
  revenue?: number;
}

interface TrendingEvent {
  event_id?: string;
  title?: string;
  reservations?: number;
  count?: number;
}

interface RealtimeData {
  current?: RealtimeCurrent;
  lastHour?: RealtimeLastHour;
  trendingEvents?: TrendingEvent[];
}

interface OverviewData {
  total_users?: number;
  active_events?: number;
  confirmed_reservations?: number;
  total_revenue?: number;
}

interface ConversionFunnel {
  total_started?: number;
  completed?: number;
  pending?: number;
  cancelled?: number;
}

interface ConversionRates {
  conversion_rate?: number;
  cancellation_rate?: number;
  pending_rate?: number;
}

interface ConversionData {
  funnel?: ConversionFunnel;
  rates?: ConversionRates;
}

interface HourlyTrafficData {
  hourly?: HourlyDataPoint[];
  peakHour?: PeakHour;
}

interface CancellationOverview {
  total_cancelled?: number;
  avg_hours_before_cancel?: number;
  total_refund_amount?: number;
}

interface CancelByEvent {
  event_id?: string;
  title?: string;
  event_title?: string;
  cancelled_count?: number;
  total_cancelled?: number;
  refund_amount?: number;
  total_refund?: number;
}

interface CancellationsData {
  overview?: CancellationOverview;
  byEvent?: CancelByEvent[];
}

interface SeatPreferencesData {
  bySection?: SeatSection[];
  byPriceTier?: PriceTier[];
}

interface AverageMetrics {
  avg_reservations?: number;
  avg_spending?: number;
}

interface UserBehaviorData {
  userTypes?: UserType[];
  spendingDistribution?: SpendingTier[];
  averageMetrics?: AverageMetrics;
}

interface DatabaseInfo {
  size?: string;
  tableCounts?: number | Record<string, number>;
}

interface RecentPerformance {
  successRate?: number;
}

interface PerformanceData {
  database?: DatabaseInfo;
  recentPerformance?: RecentPerformance;
}

interface EventStatItem {
  event_id?: string;
  title?: string;
  total_reservations?: number;
  total_revenue?: number;
  seat_utilization?: number;
}

interface EventsResponse {
  events?: EventStatItem[];
}

/* ───────── data extraction helpers ───────── */

interface ApiResponseData {
  data?: Record<string, unknown>;
}

function extract<T>(res: AxiosResponse<ApiResponseData | T>): T | null {
  const d = res?.data as Record<string, unknown> | undefined;
  return (d?.data ?? d ?? null) as T | null;
}

function extractArray<T>(res: AxiosResponse<ApiResponseData | T[]>, key?: string): T[] {
  const d = res?.data as Record<string, unknown> | undefined;
  const inner = d?.data ?? d;
  if (key && inner && typeof inner === "object" && key in inner) {
    return (inner as Record<string, unknown>)[key] as T[];
  }
  if (Array.isArray(inner)) return inner as T[];
  return [];
}

/* ───────── page ───────── */

export default function AdminStatisticsPage() {
  /* ── state for all 12 sections ── */
  const [loading, setLoading] = useState(true);
  const [realtime, setRealtime] = useState<RealtimeData | null>(null);
  const [overview, setOverview] = useState<OverviewData | null>(null);
  const [conversion, setConversion] = useState<ConversionData | null>(null);
  const [hourly, setHourly] = useState<HourlyTrafficData | null>(null);
  const [daily, setDaily] = useState<DailyDataPoint[]>([]);
  const [revenue, setRevenue] = useState<RevenueDataPoint[]>([]);
  const [cancellations, setCancellations] = useState<CancellationsData | null>(null);
  const [seatPrefs, setSeatPrefs] = useState<SeatPreferencesData | null>(null);
  const [userBehavior, setUserBehavior] = useState<UserBehaviorData | null>(null);
  const [performance, setPerformance] = useState<PerformanceData | null>(null);
  const [eventStats, setEventStats] = useState<EventStatItem[]>([]);
  const [payments, setPayments] = useState<PaymentMethod[]>([]);

  /* ── realtime fetcher (called on mount + every 30s) ── */
  const fetchRealtime = useCallback(() => {
    statsApi
      .realtime()
      .then((res) => setRealtime(extract<RealtimeData>(res)))
      .catch(() => {});
  }, []);

  /* ── initial parallel fetch ── */
  useEffect(() => {
    Promise.all([
      statsApi.realtime().then((res) => setRealtime(extract<RealtimeData>(res))).catch(() => {}),
      statsApi.overview().then((res) => setOverview(extract<OverviewData>(res))).catch(() => {}),
      statsApi.conversion(30).then((res) => setConversion(extract<ConversionData>(res))).catch(() => {}),
      statsApi.hourlyTraffic(7).then((res) => setHourly(extract<HourlyTrafficData>(res))).catch(() => {}),
      statsApi.daily(30).then((res) => {
        const arr = extractArray<DailyDataPoint>(res, "daily");
        setDaily([...arr].reverse());
      }).catch(() => {}),
      statsApi.revenue({ period: "daily", days: 30 }).then((res) => {
        const arr = extractArray<RevenueDataPoint>(res);
        setRevenue([...arr].reverse());
      }).catch(() => {}),
      statsApi.cancellations(30).then((res) => setCancellations(extract<CancellationsData>(res))).catch(() => {}),
      statsApi.seatPreferences().then((res) => setSeatPrefs(extract<SeatPreferencesData>(res))).catch(() => {}),
      statsApi.userBehavior(30).then((res) => setUserBehavior(extract<UserBehaviorData>(res))).catch(() => {}),
      statsApi.performance().then((res) => setPerformance(extract<PerformanceData>(res))).catch(() => {}),
      statsApi.events({ limit: 20, sortBy: "revenue" }).then((res) => {
        const d = extract<EventsResponse>(res);
        setEventStats(d?.events ?? (Array.isArray(d) ? d as unknown as EventStatItem[] : []));
      }).catch(() => {}),
      statsApi.payments().then((res) => setPayments(extractArray<PaymentMethod>(res))).catch(() => {}),
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
  const hourlyData: HourlyDataPoint[] = hourly?.hourly ?? [];
  const peakHour: PeakHour | null = hourly?.peakHour ?? null;
  const cancelOverview = cancellations?.overview;
  const cancelByEvent: CancelByEvent[] = cancellations?.byEvent ?? [];
  const seatSections: SeatSection[] = seatPrefs?.bySection ?? [];
  const priceTiers: PriceTier[] = seatPrefs?.byPriceTier ?? [];
  const userTypes: UserType[] = userBehavior?.userTypes ?? [];
  const spendingDist: SpendingTier[] = userBehavior?.spendingDistribution ?? [];
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
            <Spinner size="sm" className="border-sky-500 border-t-transparent" />
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
                    {realtime!.trendingEvents!.map((ev: TrendingEvent, i: number) => (
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
                Charts (dynamically loaded)
                Sections 4-6, 8-9, 12
            ═══════════════════════════════════════════ */}
            <StatisticsCharts
              hourlyData={hourlyData}
              peakHour={peakHour}
              daily={daily}
              revenue={revenue}
              seatSections={seatSections}
              priceTiers={priceTiers}
              userTypes={userTypes}
              spendingDist={spendingDist}
              payments={payments}
            />

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
                        {cancelByEvent.map((ev: CancelByEvent, i: number) => (
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
                9b. 사용자 행동 - Average Metrics
            ═══════════════════════════════════════════ */}
            {avgMetrics && (
              <section>
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
              </section>
            )}

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
                        {eventStats.map((ev: EventStatItem, i: number) => (
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
          </div>
        )}
      </div>
    </AuthGuard>
  );
}
