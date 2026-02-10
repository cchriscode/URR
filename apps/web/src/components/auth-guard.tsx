"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { getToken, getUser } from "@/lib/storage";

interface Props {
  children: React.ReactNode;
  adminOnly?: boolean;
}

export function AuthGuard({ children, adminOnly = false }: Props) {
  const router = useRouter();
  const token = getToken();
  const user = getUser();
  const unauthorized = !token || (adminOnly && user?.role !== "admin");

  useEffect(() => {
    if (!token) {
      router.replace("/login");
      return;
    }

    if (adminOnly && user?.role !== "admin") {
      router.replace("/");
    }
  }, [adminOnly, router, token, user]);

  if (unauthorized) {
    return (
      <div className="flex items-center justify-center py-20">
        <div className="text-center">
          <div className="inline-block h-6 w-6 animate-spin rounded-full border-2 border-sky-500 border-t-transparent" />
          <p className="mt-3 text-sm text-slate-500">Authorizing...</p>
        </div>
      </div>
    );
  }

  return <>{children}</>;
}
