"use client";

import { useQuery } from "@tanstack/react-query";
import { reservationsApi } from "@/lib/api-client";

export function useMyReservations() {
  return useQuery({
    queryKey: ["reservations", "mine"],
    queryFn: async () => {
      const res = await reservationsApi.mine();
      return res.data.reservations ?? res.data.data ?? [];
    },
  });
}
