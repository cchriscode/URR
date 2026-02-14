"use client";

import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { useEffect } from "react";

interface Props {
  children: React.ReactNode;
  adminOnly?: boolean;
}

export function AuthGuard({ children, adminOnly = false }: Props) {
  const router = useRouter();
  const { user, isLoading } = useAuth();

  useEffect(() => {
    if (isLoading) return;
    if (!user) {
      router.replace("/login");
      return;
    }
    if (adminOnly && user.role !== "admin") {
      router.replace("/");
    }
  }, [user, isLoading, adminOnly, router]);

  if (isLoading || !user) {
    return (
      <div className="flex items-center justify-center py-20" role="status" aria-label="인증 확인 중">
        <div className="text-center">
          <div className="inline-block h-6 w-6 animate-spin rounded-full border-2 border-sky-500 border-t-transparent" aria-hidden="true" />
          <p className="mt-3 text-sm text-slate-500">인증 확인 중...</p>
        </div>
      </div>
    );
  }

  if (adminOnly && user.role !== "admin") {
    return null;
  }

  return <>{children}</>;
}
