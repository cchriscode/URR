"use client";

import { useState } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { clearAuth, getUser } from "@/lib/storage";
import { authApi } from "@/lib/api-client";

export function SiteHeader() {
  const pathname = usePathname();
  const router = useRouter();
  const user = getUser();
  const [searchText, setSearchText] = useState("");

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    const q = searchText.trim();
    if (q) {
      router.push(`/search?q=${encodeURIComponent(q)}`);
    }
  };

  return (
    <header className="sticky top-0 z-40 border-b border-slate-200 bg-white">
      <div className="mx-auto flex max-w-7xl items-center justify-between gap-4 px-6 py-3">
        {/* Logo */}
        <Link href="/" className="flex items-center gap-1.5 font-bold text-lg tracking-tight text-sky-500 shrink-0">
          <span className="text-amber-400 text-xl">&#9889;</span>
          URR
        </Link>

        {/* Search bar */}
        <form onSubmit={handleSearch} role="search" aria-label="공연 및 아티스트 검색" className="flex flex-1 max-w-md">
          <input
            type="search"
            aria-label="검색어 입력"
            className="flex-1 rounded-l-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-900 placeholder:text-slate-400 focus:border-sky-400 focus:outline-none focus:ring-1 focus:ring-sky-400"
            placeholder="공연, 아티스트 검색"
            value={searchText}
            onChange={(e) => setSearchText(e.target.value)}
          />
          <button
            type="submit"
            className="rounded-r-lg bg-sky-500 px-4 py-2 text-sm font-medium text-white hover:bg-sky-600 transition-colors"
          >
            검색
          </button>
        </form>

        {/* Nav */}
        <nav aria-label="메인 내비게이션" className="flex items-center gap-1 text-sm shrink-0">
          <Link
            href="/artists"
            className={`rounded-lg px-3 py-1.5 transition-colors ${
              pathname === "/artists" || pathname.startsWith("/artists/")
                ? "bg-sky-50 text-sky-600 font-medium"
                : "text-slate-600 hover:text-sky-500 hover:bg-slate-50"
            }`}
          >
            아티스트
          </Link>
          <Link
            href="/community"
            className={`rounded-lg px-3 py-1.5 transition-colors ${
              pathname.startsWith("/community")
                ? "bg-sky-50 text-sky-600 font-medium"
                : "text-slate-600 hover:text-sky-500 hover:bg-slate-50"
            }`}
          >
            커뮤니티
          </Link>
          {user ? (
            <>
              <Link
                href="/my-reservations"
                className={`rounded-lg px-3 py-1.5 transition-colors ${
                  pathname === "/my-reservations"
                    ? "bg-sky-50 text-sky-600 font-medium"
                    : "text-slate-600 hover:text-sky-500 hover:bg-slate-50"
                }`}
              >
                내 예매
              </Link>
              <Link
                href="/my-memberships"
                className={`rounded-lg px-3 py-1.5 transition-colors ${
                  pathname === "/my-memberships"
                    ? "bg-sky-50 text-sky-600 font-medium"
                    : "text-slate-600 hover:text-sky-500 hover:bg-slate-50"
                }`}
              >
                내 멤버십
              </Link>
              <Link
                href="/transfers"
                className={`rounded-lg px-3 py-1.5 transition-colors ${
                  pathname === "/transfers" || pathname.startsWith("/transfers/")
                    ? "bg-sky-50 text-sky-600 font-medium"
                    : "text-slate-600 hover:text-sky-500 hover:bg-slate-50"
                }`}
              >
                양도 마켓
              </Link>
              {user.role === "admin" && (
                <Link
                  href="/admin"
                  className={`rounded-lg px-3 py-1.5 transition-colors ${
                    pathname.startsWith("/admin")
                      ? "bg-sky-50 text-sky-600 font-medium"
                      : "text-slate-600 hover:text-sky-500 hover:bg-slate-50"
                  }`}
                >
                  관리자
                </Link>
              )}
              <span className="ml-2 text-slate-600">{user.name}</span>
              <button
                type="button"
                className="ml-1 rounded-lg border border-slate-200 px-3 py-1.5 text-slate-600 hover:border-sky-300 hover:text-sky-500 transition-colors"
                onClick={async () => {
                  try { await authApi.logout(); } catch {}
                  clearAuth();
                  router.push("/login");
                }}
              >
                로그아웃
              </button>
            </>
          ) : (
            <>
              <Link className="rounded-lg px-3 py-1.5 text-slate-600 hover:text-sky-500 transition-colors" href="/login">
                로그인
              </Link>
              <Link
                className="rounded-lg bg-sky-500 px-3 py-1.5 text-white hover:bg-sky-600 transition-colors"
                href="/register"
              >
                회원가입
              </Link>
            </>
          )}
        </nav>
      </div>
    </header>
  );
}
