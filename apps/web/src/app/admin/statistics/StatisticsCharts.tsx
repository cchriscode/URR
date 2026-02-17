"use client";

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

/* ───────── helpers ───────── */

function won(v: number | null | undefined): string {
  if (v == null) return "0원";
  return `${v.toLocaleString()}원`;
}

function num(v: number | null | undefined): string {
  if (v == null) return "0";
  return v.toLocaleString();
}

/* ───────── types ───────── */

export interface HourlyDataPoint {
  hour: number;
  total_reservations: number;
}

export interface DailyDataPoint {
  date: string;
  reservations: number;
  confirmed: number;
}

export interface RevenueDataPoint {
  period: string;
  total_revenue: number;
}

export interface SeatSection {
  section: string;
  reserved_seats: number;
  total_seats: number;
}

export interface PriceTier {
  tier?: string;
  price_tier?: string;
  reserved_seats?: number;
  count?: number;
  reservations?: number;
}

export interface UserType {
  user_type: string;
  user_count: number;
}

export interface SpendingTier {
  range?: string;
  tier?: string;
  user_count?: number;
  count?: number;
}

export interface PaymentMethod {
  method: string;
  count: number;
  total_amount: number;
  average_amount: number;
}

export interface PeakHour {
  hour: number;
  reservations: number;
}

export interface StatisticsChartsProps {
  hourlyData: HourlyDataPoint[];
  peakHour: PeakHour | null;
  daily: DailyDataPoint[];
  revenue: RevenueDataPoint[];
  seatSections: SeatSection[];
  priceTiers: PriceTier[];
  userTypes: UserType[];
  spendingDist: SpendingTier[];
  payments: PaymentMethod[];
}

export default function StatisticsCharts({
  hourlyData,
  peakHour,
  daily,
  revenue,
  seatSections,
  priceTiers,
  userTypes,
  spendingDist,
  payments,
}: StatisticsChartsProps) {
  return (
    <>
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
                  formatter={(v) => [`${(v as number) ?? 0}건`, "예매"]}
                  labelFormatter={(h) => `${h}시`}
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
                  labelFormatter={(d) => `${d}`}
                  formatter={(v, name) => [
                    `${(v as number) ?? 0}건`,
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
                  formatter={(v) => [won(v as number), "매출"]}
                  labelFormatter={(d) => `${d}`}
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
                    label={(entry) => {
                      const p = entry as typeof entry & { section: string };
                      return `${p.section} ${((p.percent ?? 0) * 100).toFixed(0)}%`;
                    }}
                  >
                    {seatSections.map((_: SeatSection, idx: number) => (
                      <Cell
                        key={idx}
                        fill={COLORS[idx % COLORS.length]}
                      />
                    ))}
                  </Pie>
                  <Tooltip
                    formatter={(v, _name, entry) => [
                      `${(v as number) ?? 0}석 / ${(entry as { payload?: SeatSection })?.payload?.total_seats ?? "-"}석`,
                      (entry as { payload?: SeatSection })?.payload?.section,
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
                {priceTiers.map((tier: PriceTier, idx: number) => {
                  const maxCount = Math.max(
                    ...priceTiers.map(
                      (t: PriceTier) =>
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
          9. 사용자 행동 - Charts (User Behavior)
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
                    label={(entry) => {
                      const p = entry as typeof entry & { user_type: string };
                      const labelMap: Record<string, string> = {
                        new: "신규",
                        returning: "재방문",
                        loyal: "충성",
                      };
                      return `${labelMap[p.user_type] ?? p.user_type} ${((p.percent ?? 0) * 100).toFixed(0)}%`;
                    }}
                  >
                    {userTypes.map((_: UserType, idx: number) => (
                      <Cell
                        key={idx}
                        fill={COLORS[idx % COLORS.length]}
                      />
                    ))}
                  </Pie>
                  <Tooltip
                    formatter={(v, _name, entry) => {
                      const labelMap: Record<string, string> = {
                        new: "신규",
                        returning: "재방문",
                        loyal: "충성",
                      };
                      const raw = (entry as { payload?: UserType })?.payload?.user_type ?? "";
                      return [
                        `${(v as number) ?? 0}명`,
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

          {/* Spending distribution */}
          <div className="rounded-xl border border-slate-200 bg-white p-5">
            <p className="text-xs font-medium uppercase tracking-wider text-slate-400 mb-3">
              지출 분포
            </p>
            {spendingDist.length > 0 ? (
              <div className="space-y-2">
                {spendingDist.map((tier: SpendingTier, idx: number) => {
                  const maxVal = Math.max(
                    ...spendingDist.map(
                      (t: SpendingTier) => t?.user_count ?? t?.count ?? 0
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
                    label={(entry) => {
                      const p = entry as typeof entry & { method: string };
                      return `${p.method} ${((p.percent ?? 0) * 100).toFixed(0)}%`;
                    }}
                  >
                    {payments.map((_: PaymentMethod, idx: number) => (
                      <Cell
                        key={idx}
                        fill={COLORS[idx % COLORS.length]}
                      />
                    ))}
                  </Pie>
                  <Tooltip
                    formatter={(v, _name, entry) => [
                      `${(v as number) ?? 0}건`,
                      (entry as { payload?: PaymentMethod })?.payload?.method,
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
                    {payments.map((p: PaymentMethod, idx: number) => (
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
    </>
  );
}
