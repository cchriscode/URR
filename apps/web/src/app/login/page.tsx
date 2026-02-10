"use client";

import { FormEvent, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { authApi } from "@/lib/api-client";
import { setToken, setUser } from "@/lib/storage";
export default function LoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError(null);
    try {
      const { data } = await authApi.login({ email, password });
      setToken(data.token);
      setUser(data.user);
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
