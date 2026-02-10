"use client";

import { FormEvent, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { AuthGuard } from "@/components/auth-guard";
import { adminApi } from "@/lib/api-client";
import type { SeatLayout } from "@/lib/types";

interface TicketTypeInput {
  name: string;
  price: string;
  totalQuantity: string;
  description: string;
}

export default function AdminEventNewPage() {
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
  const [ticketTypes, setTicketTypes] = useState<TicketTypeInput[]>([]);
  const [generateSeats, setGenerateSeats] = useState(true);
  const [loading, setLoading] = useState(false);
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

  const addTicketType = () => {
    setTicketTypes([...ticketTypes, { name: "", price: "", totalQuantity: "", description: "" }]);
  };

  const removeTicketType = (index: number) => {
    setTicketTypes(ticketTypes.filter((_, i) => i !== index));
  };

  const updateTicketType = (index: number, field: keyof TicketTypeInput, value: string) => {
    const updated = [...ticketTypes];
    updated[index] = { ...updated[index], [field]: value };
    setTicketTypes(updated);
  };

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError(null);
    try {
      const payload: Record<string, unknown> = {
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
      };

      if (ticketTypes.length > 0) {
        payload.ticketTypes = ticketTypes
          .filter((t) => t.name && t.price)
          .map((t) => ({
            name: t.name,
            price: Number(t.price),
            totalQuantity: Number(t.totalQuantity) || 100,
            description: t.description || undefined,
          }));
      }

      const res = await adminApi.events.create(payload);
      const eventId = res.data?.id ?? res.data?.event?.id;

      if (eventId && form.seatLayoutId && generateSeats) {
        await adminApi.events.generateSeats(eventId).catch(() => {});
      }

      router.push("/admin/events");
    } catch {
      setError("이벤트 생성에 실패했습니다.");
    } finally {
      setLoading(false);
    }
  };

  const inputCls = "w-full rounded-lg border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:border-sky-400 focus:outline-none focus:ring-1 focus:ring-sky-400";

  return (
    <AuthGuard adminOnly>
      <div className="max-w-2xl mx-auto">
        <Link href="/admin/events" className="text-sm text-slate-500 hover:text-sky-500 mb-4 inline-block">
          &larr; 이벤트 목록
        </Link>
        <div className="rounded-2xl border border-slate-200 bg-white p-6">
          <h1 className="text-xl font-bold text-slate-900 mb-6">새 이벤트 만들기</h1>
          <form className="space-y-4" onSubmit={onSubmit}>
            {/* Basic Info */}
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

            {/* Dates */}
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

            {/* Status */}
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1">상태</label>
              <select className={inputCls} value={form.status} onChange={(e) => setForm({ ...form, status: e.target.value })}>
                <option value="on_sale">예매 중</option>
                <option value="upcoming">오픈 예정</option>
                <option value="ended">종료</option>
              </select>
            </div>

            {/* Poster */}
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1">포스터 이미지 URL</label>
              <input className={inputCls} placeholder="https://..." value={form.posterImageUrl} onChange={(e) => setForm({ ...form, posterImageUrl: e.target.value })} />
            </div>

            {/* Seat Layout */}
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1">좌석 레이아웃</label>
              <select className={inputCls} value={form.seatLayoutId} onChange={(e) => setForm({ ...form, seatLayoutId: e.target.value })}>
                <option value="">선택 안함 (티켓 방식)</option>
                {layouts.map((layout) => (
                  <option key={layout.id} value={layout.id}>
                    {layout.name} - {layout.description} ({layout.total_seats}석)
                  </option>
                ))}
              </select>
              {form.seatLayoutId && (
                <label className="flex items-center gap-2 mt-2 text-sm text-slate-600">
                  <input type="checkbox" checked={generateSeats} onChange={(e) => setGenerateSeats(e.target.checked)} className="rounded" />
                  이벤트 생성 시 자동으로 좌석 생성
                </label>
              )}
            </div>

            {/* Description */}
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1">설명</label>
              <textarea className={inputCls} placeholder="이벤트 설명" rows={3} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
            </div>

            {/* Ticket Types */}
            <div className="border-t border-slate-100 pt-4">
              <div className="flex items-center justify-between mb-3">
                <label className="text-sm font-medium text-slate-700">티켓 종류</label>
                <button type="button" onClick={addTicketType} className="text-xs text-sky-500 hover:text-sky-600 font-medium">
                  + 추가
                </button>
              </div>
              {ticketTypes.length === 0 ? (
                <p className="text-xs text-slate-400">티켓 종류를 추가하면 이벤트와 함께 생성됩니다.</p>
              ) : (
                <div className="space-y-3">
                  {ticketTypes.map((tt, i) => (
                    <div key={i} className="rounded-lg border border-slate-100 bg-slate-50 p-3 space-y-2">
                      <div className="flex items-center justify-between">
                        <span className="text-xs font-medium text-slate-500">티켓 #{i + 1}</span>
                        <button type="button" onClick={() => removeTicketType(i)} className="text-xs text-red-400 hover:text-red-500">삭제</button>
                      </div>
                      <div className="grid grid-cols-3 gap-2">
                        <input className={inputCls} placeholder="이름 (VIP석)" value={tt.name} onChange={(e) => updateTicketType(i, "name", e.target.value)} />
                        <input className={inputCls} type="number" placeholder="가격" value={tt.price} onChange={(e) => updateTicketType(i, "price", e.target.value)} />
                        <input className={inputCls} type="number" placeholder="수량" value={tt.totalQuantity} onChange={(e) => updateTicketType(i, "totalQuantity", e.target.value)} />
                      </div>
                      <input className={inputCls} placeholder="설명 (선택)" value={tt.description} onChange={(e) => updateTicketType(i, "description", e.target.value)} />
                    </div>
                  ))}
                </div>
              )}
            </div>

            <button
              className="w-full rounded-lg bg-sky-500 px-4 py-2.5 text-sm font-medium text-white hover:bg-sky-600 disabled:opacity-50 transition-colors"
              type="submit"
              disabled={loading}
            >
              {loading ? "생성 중..." : "이벤트 생성"}
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
