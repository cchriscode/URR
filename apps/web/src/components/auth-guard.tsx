"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { getUser } from "@/lib/storage";
import { authApi } from "@/lib/api-client";

interface Props {
  children: React.ReactNode;
  adminOnly?: boolean;
}

export function AuthGuard({ children, adminOnly = false }: Props) {
  const router = useRouter();
  const [verified, setVerified] = useState(false);

  useEffect(() => {
    // Read user inside effect to avoid object-reference dep changes
    const user = getUser();

    if (!user) {
      router.replace("/login");
      return;
    }

    if (adminOnly && user.role !== "admin") {
      router.replace("/");
      return;
    }

    // Short-term cache: skip /auth/me if verified within last 30 seconds
    const cacheKey = "auth-me-ts";
    const cached = sessionStorage.getItem(cacheKey);
    if (cached && Date.now() - Number(cached) < 30_000) {
      setVerified(true);
      return;
    }

    // Server-side verification via /me endpoint (cookie-based auth).
    authApi
      .me()
      .then((res) => {
        const serverUser = res.data?.user ?? res.data;
        if (adminOnly && serverUser.role !== "admin") {
          router.replace("/");
        } else {
          sessionStorage.setItem(cacheKey, String(Date.now()));
          setVerified(true);
        }
      })
      .catch(() => {
        // For non-401 errors (network, 429, 500): trust local user data as fallback.
        // Still set the cache so we don't hammer /auth/me on every navigation.
        const localUser = getUser();
        if (localUser === null) {
          router.replace("/login");
        } else if (adminOnly && localUser.role !== "admin") {
          router.replace("/");
        } else {
          sessionStorage.setItem(cacheKey, String(Date.now()));
          setVerified(true);
        }
      });
  }, [adminOnly, router]);

  if (!verified) {
    return (
      <div className="flex items-center justify-center py-20" role="status" aria-label="인증 확인 중">
        <div className="text-center">
          <div className="inline-block h-6 w-6 animate-spin rounded-full border-2 border-sky-500 border-t-transparent" aria-hidden="true" />
          <p className="mt-3 text-sm text-slate-500">인증 확인 중...</p>
        </div>
      </div>
    );
  }

  return <>{children}</>;
}
