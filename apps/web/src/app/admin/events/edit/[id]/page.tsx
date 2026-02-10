"use client";

import { FormEvent, useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { AuthGuard } from "@/components/auth-guard";
import { eventsApi, adminApi } from "@/lib/api-client";
import type { SeatLayout } from "@/lib/types";

export default function AdminEventEditPage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const [layouts, setLayouts] = useState<SeatLayout[]>([]);
  const [form, setForm] = useState({
    title: "",
    description: "",
    venue: "",
    address: "",
    eventDate: "",
    saleStartDate: "",
    saleEndDate: "",
    posterImageUrl: "",
    artistName: "",
    seatLayoutId: "",
    status: "on_sale",
  });
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    adminApi.seatLayouts()
      .then((res) => {
        const raw = res.data?.layouts ?? res.data?.data ?? res.data ?? [];
        const parsed = (Array.isArray(raw) ? raw : []).map((l: Record<string, unknown>) => {
          let cfg = l.layout_config as Record<string, unknown> | undefined;
          if (cfg && typeof cfg === "object" && "value" in cfg && typeof cfg.value === "string") {
            try { cfg = JSON.parse(cfg.value as string); } catch { /* keep as-is */ }
          }
          return { ...l, layout_config: cfg } as SeatLayout;
        });
        setLayouts(parsed);
      })
      .catch(() => {});
  }, []);

  useEffect(() => {
    if (!params.id) return;
    eventsApi
      .detail(params.id)
      .then((res) => {
        const event = res.data.event ?? res.data.data ?? res.data ?? {};
        const toLocal = (d: string | undefined) => {
          if (!d) return "";
          try {
            return new Date(d).toISOString().slice(0, 16);
          } catch {
            return d;
          }
        };
        setForm({
          title: event.title ?? "",
          description: event.description ?? "",
          venue: event.venue ?? "",
          address: event.address ?? "",
          eventDate: toLocal(event.event_date ?? event.eventDate),
          saleStartDate: toLocal(event.sale_start_date ?? event.saleStartDate),
          saleEndDate: toLocal(event.sale_end_date ?? event.saleEndDate),
          posterImageUrl: event.poster_image_url ?? event.posterImageUrl ?? "",
          artistName: event.artist_name ?? event.artistName ?? "",
          seatLayoutId: event.seat_layout_id ?? event.seatLayoutId ?? "",
          status: event.status ?? "on_sale",
        });
      })
      .finally(() => setLoading(false));
  }, [params.id]);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!params.id) return;
    setSaving(true);
    setError(null);
    try {
      await adminApi.events.update(params.id, {
        title: form.title,
        description: form.description || undefined,
        venue: form.venue,
        address: form.address || undefined,
        eventDate: form.eventDate ? new Date(form.eventDate).toISOString() : undefined,
        saleStartDate: form.saleStartDate ? new Date(form.saleStartDate).toISOString() : undefined,
        saleEndDate: form.saleEndDate ? new Date(form.saleEndDate).toISOString() : undefined,
        posterImageUrl: form.posterImageUrl || undefined,
        artistName: form.artistName || undefined,
        seatLayoutId: form.seatLayoutId || undefined,
        status: form.status,
      });
      router.push("/admin/events");
    } catch {
      setError("이벤트 수정에 실패했습니다.");
    } finally {
      setSaving(false);
    }
  };

  const inputCls = "w-full rounded-lg border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:border-sky-400 focus:outline-none focus:ring-1 focus:ring-sky-400";

  if (loading) {
    return (
      <AuthGuard adminOnly>
        <div className="flex justify-center py-20">
          <div className="inline-block h-6 w-6 animate-spin rounded-full border-2 border-sky-500 border-t-transparent" />
        </div>
      </AuthGuard>
    );
  }

  return (
    <AuthGuard adminOnly>
      <div className="max-w-2xl mx-auto">
        <Link href="/admin/events" className="text-sm text-slate-500 hover:text-sky-500 mb-4 inline-block">
          &larr; 이벤트 목록
        </Link>
        <div className="rounded-2xl border border-slate-200 bg-white p-6">
          <h1 className="text-xl font-bold text-slate-900 mb-6">이벤트 수정</h1>
          <form className="space-y-4" onSubmit={onSubmit}>
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1">제목 *</label>
              <input className={inputCls} placeholder="이벤트 제목" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} required />
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1">아티스트</label>
              <input className={inputCls} placeholder="아티스트 / 출연자" value={form.artistName} onChange={(e) => setForm({ ...form, artistName: e.target.value })} />
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1">장소 *</label>
                <input className={inputCls} placeholder="공연장" value={form.venue} onChange={(e) => setForm({ ...form, venue: e.target.value })} required />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1">주소</label>
                <input className={inputCls} placeholder="상세 주소" value={form.address} onChange={(e) => setForm({ ...form, address: e.target.value })} />
              </div>
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1">공연 일시 *</label>
              <input type="datetime-local" className={inputCls} value={form.eventDate} onChange={(e) => setForm({ ...form, eventDate: e.target.value })} required />
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1">판매 시작</label>
                <input type="datetime-local" className={inputCls} value={form.saleStartDate} onChange={(e) => setForm({ ...form, saleStartDate: e.target.value })} />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1">판매 종료</label>
                <input type="datetime-local" className={inputCls} value={form.saleEndDate} onChange={(e) => setForm({ ...form, saleEndDate: e.target.value })} />
              </div>
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1">상태</label>
              <select className={inputCls} value={form.status} onChange={(e) => setForm({ ...form, status: e.target.value })}>
                <option value="on_sale">예매 중</option>
                <option value="upcoming">오픈 예정</option>
                <option value="ended">종료</option>
                <option value="cancelled">취소</option>
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1">포스터 이미지 URL</label>
              <input className={inputCls} placeholder="https://..." value={form.posterImageUrl} onChange={(e) => setForm({ ...form, posterImageUrl: e.target.value })} />
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1">좌석 레이아웃</label>
              <select className={inputCls} value={form.seatLayoutId} onChange={(e) => setForm({ ...form, seatLayoutId: e.target.value })}>
                <option value="">선택 안함</option>
                {layouts.map((layout) => (
                  <option key={layout.id} value={layout.id}>
                    {layout.name} - {layout.description} ({layout.total_seats}석)
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1">설명</label>
              <textarea className={inputCls} placeholder="이벤트 설명" rows={3} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
            </div>
            <button
              className="w-full rounded-lg bg-sky-500 px-4 py-2.5 text-sm font-medium text-white hover:bg-sky-600 disabled:opacity-50 transition-colors"
              type="submit"
              disabled={saving}
            >
              {saving ? "저장 중..." : "변경사항 저장"}
            </button>
            {error ? (
              <p className="rounded-lg bg-red-50 border border-red-200 px-3 py-2 text-sm text-red-600">{error}</p>
            ) : null}
          </form>
        </div>
      </div>
    </AuthGuard>
  );
}
