"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { AuthGuard } from "@/components/auth-guard";
import { transfersApi, artistsApi, reservationsApi } from "@/lib/api-client";
import { useAuth } from "@/lib/auth-context";
import { formatEventDate } from "@/lib/format";
import type { TicketTransfer, Artist } from "@/lib/types";

interface MyReservation {
  id: string;
  event_title?: string;
  event_date?: string;
  venue?: string;
  total_amount?: number;
  status?: string;
}

export default function TransfersPage() {
  const [transfers, setTransfers] = useState<TicketTransfer[]>([]);
  const [artists, setArtists] = useState<Artist[]>([]);
  const [selectedArtist, setSelectedArtist] = useState("");
  const [loading, setLoading] = useState(true);
  const [showRegister, setShowRegister] = useState(false);
  const [myReservations, setMyReservations] = useState<MyReservation[]>([]);
  const [loadingRes, setLoadingRes] = useState(false);
  const [registering, setRegistering] = useState<string | null>(null);
  const { user: currentUser } = useAuth();

  useEffect(() => {
    artistsApi
      .list({ limit: 100 })
      .then((res) => setArtists(res.data.artists ?? []))
      .catch(() => {});
  }, []);

  const loadTransfers = () => {
    setLoading(true);
    const params: Record<string, string | number> = {};
    if (selectedArtist) params.artistId = selectedArtist;
    transfersApi
      .list(params)
      .then((res) => setTransfers(res.data.transfers ?? []))
      .catch(() => setTransfers([]))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    loadTransfers();
  }, [selectedArtist]);

  const handleOpenRegister = () => {
    setShowRegister(true);
    setLoadingRes(true);
    reservationsApi
      .mine()
      .then((res) => {
        const all = res.data.reservations ?? res.data.data ?? [];
        setMyReservations(all.filter((r: MyReservation) => r.status === "confirmed"));
      })
      .catch(() => setMyReservations([]))
      .finally(() => setLoadingRes(false));
  };

  const handleRegisterTransfer = async (reservationId: string) => {
    if (!confirm("이 티켓을 양도 마켓에 등록하시겠습니까?")) return;
    setRegistering(reservationId);
    try {
      await transfersApi.create(reservationId);
      alert("양도 등록이 완료되었습니다.");
      setShowRegister(false);
      loadTransfers();
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string; error?: string } } })?.response?.data?.message
        ?? (err as { response?: { data?: { error?: string } } })?.response?.data?.error;
      alert(msg ?? "양도 등록에 실패했습니다.");
    } finally {
      setRegistering(null);
    }
  };

  return (
    <AuthGuard>
      <div className="max-w-4xl mx-auto">
        <div className="flex items-center justify-between mb-6">
          <h1 className="text-2xl font-bold text-slate-900">양도 마켓</h1>
          <div className="flex items-center gap-3">
            <button
              onClick={handleOpenRegister}
              className="rounded-lg bg-amber-500 px-4 py-2 text-sm font-medium text-white hover:bg-amber-600 transition-colors"
            >
              내 티켓 양도하기
            </button>
            <Link
              href="/transfers/my"
              className="text-sm text-sky-500 hover:text-sky-600 font-medium"
            >
              내 양도 목록 &rarr;
            </Link>
          </div>
        </div>

        {/* Register transfer panel */}
        {showRegister && (
          <div className="mb-4 rounded-2xl border border-amber-200 bg-amber-50 p-5">
            <div className="flex items-center justify-between mb-3">
              <h2 className="text-sm font-bold text-amber-800">양도할 티켓 선택</h2>
              <button
                onClick={() => setShowRegister(false)}
                className="text-xs text-slate-400 hover:text-slate-600"
              >
                닫기
              </button>
            </div>
            {loadingRes ? (
              <div className="flex justify-center py-4">
                <div className="inline-block h-5 w-5 animate-spin rounded-full border-2 border-amber-500 border-t-transparent" />
              </div>
            ) : myReservations.length === 0 ? (
              <p className="text-sm text-slate-500">양도 가능한 확정 예매가 없습니다.</p>
            ) : (
              <div className="space-y-2">
                {myReservations.map((r) => (
                  <div
                    key={r.id}
                    className="flex items-center justify-between rounded-lg border border-amber-100 bg-white p-3"
                  >
                    <div>
                      <p className="text-sm font-medium text-slate-900">{r.event_title ?? r.id.slice(0, 8)}</p>
                      <div className="flex gap-3 text-xs text-slate-400 mt-0.5">
                        {r.event_date && <span>{formatEventDate(r.event_date)}</span>}
                        {r.total_amount != null && <span>{r.total_amount.toLocaleString()}원</span>}
                      </div>
                    </div>
                    <button
                      onClick={() => handleRegisterTransfer(r.id)}
                      disabled={registering === r.id}
                      className="rounded-lg bg-amber-500 px-3 py-1.5 text-xs font-medium text-white hover:bg-amber-600 disabled:opacity-50 transition-colors"
                    >
                      {registering === r.id ? "등록 중..." : "양도 등록"}
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* Artist filter */}
        <div className="mb-4">
          <select
            value={selectedArtist}
            onChange={(e) => setSelectedArtist(e.target.value)}
            className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700 focus:border-sky-400 focus:outline-none focus:ring-1 focus:ring-sky-400"
          >
            <option value="">전체 아티스트</option>
            {artists.map((a) => (
              <option key={a.id} value={a.id}>
                {a.name}
              </option>
            ))}
          </select>
        </div>

        {loading ? (
          <div className="flex justify-center py-16">
            <div className="inline-block h-6 w-6 animate-spin rounded-full border-2 border-sky-500 border-t-transparent" />
          </div>
        ) : transfers.length === 0 ? (
          <div className="rounded-2xl border border-slate-200 bg-white p-10 text-center">
            <p className="text-slate-400 text-sm">현재 양도 가능한 티켓이 없습니다</p>
          </div>
        ) : (
          <div className="space-y-3">
            {transfers.map((t) => (
              <div
                key={t.id}
                className="rounded-xl border border-slate-200 bg-white p-5 transition-all hover:border-sky-300 hover:shadow-sm"
              >
                <div className="flex items-start justify-between gap-4">
                  <div className="flex-1 min-w-0">
                    <p className="font-medium text-slate-900">{t.event_title}</p>
                    <div className="mt-1 flex flex-wrap items-center gap-3 text-xs text-slate-400">
                      {t.artist_name && (
                        <span className="rounded-full bg-sky-50 border border-sky-100 px-2 py-0.5 text-sky-600">
                          {t.artist_name}
                        </span>
                      )}
                      {t.event_date && <span>{formatEventDate(t.event_date)}</span>}
                      {t.venue && <span>{t.venue}</span>}
                      {t.seats && <span>좌석: {t.seats}</span>}
                    </div>
                    <div className="mt-3 flex items-center gap-4 text-sm">
                      <span className="text-slate-500">
                        원가 <span className="font-medium text-slate-700">{t.original_price.toLocaleString()}원</span>
                      </span>
                      <span className="text-slate-500">
                        수수료 <span className="font-medium text-amber-600">{t.transfer_fee.toLocaleString()}원</span>
                        <span className="text-xs text-slate-400 ml-1">({t.transfer_fee_percent}%)</span>
                      </span>
                    </div>
                  </div>
                  <div className="text-right shrink-0">
                    <p className="text-lg font-bold text-sky-600">{t.total_price.toLocaleString()}원</p>
                    {currentUser?.id === t.seller_id ? (
                      <span className="mt-2 inline-block rounded-lg bg-slate-200 px-4 py-2 text-sm font-medium text-slate-400 cursor-not-allowed">
                        내 양도
                      </span>
                    ) : (
                      <Link
                        href={`/transfer-payment/${t.id}`}
                        className="mt-2 inline-block rounded-lg bg-sky-500 px-4 py-2 text-sm font-medium text-white hover:bg-sky-600 transition-colors"
                      >
                        구매하기
                      </Link>
                    )}
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </AuthGuard>
  );
}
