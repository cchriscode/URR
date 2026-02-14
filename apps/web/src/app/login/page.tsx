"use client";

import { FormEvent, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { authApi } from "@/lib/api-client";
import { useAuth } from "@/lib/auth-context";

const GOOGLE_CLIENT_ID = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID;

export default function LoginPage() {
  const router = useRouter();
  const { refresh: refreshAuth } = useAuth();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError(null);
    try {
      await authApi.login({ email, password });
      refreshAuth();
      router.push("/");
    } catch (err: unknown) {
      const resp = (err as { response?: { status?: number; data?: { message?: string; error?: string } } }).response;
      const msg =
        resp?.data?.message ??
        resp?.data?.error ??
        (resp?.status === 401
          ? "이메일 또는 비밀번호가 올바르지 않습니다."
          : `로그인 실패 (${resp?.status ?? "network error"})`);
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!GOOGLE_CLIENT_ID) return;

    const script = document.createElement("script");
    script.src = "https://accounts.google.com/gsi/client";
    script.async = true;
    script.onload = () => {
      const google = (window as unknown as Record<string, unknown>).google as {
        accounts: {
          id: {
            initialize: (config: Record<string, unknown>) => void;
            renderButton: (el: HTMLElement, config: Record<string, unknown>) => void;
          };
        };
      };
      google.accounts.id.initialize({
        client_id: GOOGLE_CLIENT_ID,
        callback: handleGoogleCallback,
      });
      const container = document.getElementById("google-signin-btn");
      if (container) {
        google.accounts.id.renderButton(container, {
          theme: "outline",
          size: "large",
          width: "100%",
          text: "signin_with",
          locale: "ko",
        });
      }
    };
    document.head.appendChild(script);
    return () => {
      script.remove();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleGoogleCallback = async (response: { credential: string }) => {
    setLoading(true);
    setError(null);
    try {
      await authApi.google(response.credential);
      refreshAuth();
      router.push("/");
    } catch (err: unknown) {
      const resp = (err as { response?: { data?: { message?: string; error?: string } } }).response;
      setError(resp?.data?.message ?? resp?.data?.error ?? "Google 로그인에 실패했습니다.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex min-h-[60vh] items-center justify-center">
      <div className="w-full max-w-sm">
        <div className="mb-8 text-center">
          <span className="text-3xl text-amber-400">&#9889;</span>
          <h1 className="mt-3 text-2xl font-bold text-slate-900">로그인</h1>
          <p className="mt-1 text-sm text-slate-500">URR 계정으로 로그인하세요</p>
        </div>
        <form className="rounded-2xl border border-slate-200 bg-white p-6 space-y-4" onSubmit={onSubmit}>
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1">이메일</label>
            <input
              className="w-full rounded-lg border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:border-sky-400 focus:outline-none focus:ring-1 focus:ring-sky-400"
              placeholder="you@example.com"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1">비밀번호</label>
            <input
              className="w-full rounded-lg border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:border-sky-400 focus:outline-none focus:ring-1 focus:ring-sky-400"
              type="password"
              placeholder="비밀번호 입력"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </div>
          <button
            className="w-full rounded-lg bg-sky-500 px-4 py-2.5 text-sm font-medium text-white hover:bg-sky-600 disabled:opacity-50 transition-colors"
            type="submit"
            disabled={loading}
          >
            {loading ? "로그인 중..." : "로그인"}
          </button>
          {error ? (
            <p className="rounded-lg bg-red-50 border border-red-200 px-3 py-2 text-sm text-red-600">{error}</p>
          ) : null}
        </form>

        {GOOGLE_CLIENT_ID && (
          <>
            <div className="my-4 flex items-center gap-3">
              <div className="h-px flex-1 bg-slate-200" />
              <span className="text-xs text-slate-400">또는</span>
              <div className="h-px flex-1 bg-slate-200" />
            </div>
            <div id="google-signin-btn" className="flex justify-center" />
          </>
        )}

        <p className="mt-4 text-center text-sm text-slate-500">
          계정이 없으신가요?{" "}
          <Link href="/register" className="text-sky-500 hover:text-sky-600 font-medium">
            회원가입
          </Link>
        </p>
      </div>
    </div>
  );
}
