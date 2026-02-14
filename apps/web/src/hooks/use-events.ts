"use client";

import { useQuery } from "@tanstack/react-query";
import { eventsApi } from "@/lib/api-client";

export function useEvents(params?: Record<string, string | number>) {
  return useQuery({
    queryKey: ["events", params],
    queryFn: async () => {
      const res = await eventsApi.list(params);
      return res.data.events ?? res.data.data ?? [];
    },
  });
}

export function useEventDetail(id: string | undefined) {
  return useQuery({
    queryKey: ["event", id],
    queryFn: async () => {
      const res = await eventsApi.detail(id!);
      const ev = res.data.event ?? res.data.data ?? res.data ?? null;
      if (ev && !ev.ticketTypes && !ev.ticket_types && res.data.ticketTypes) {
        ev.ticketTypes = res.data.ticketTypes;
      }
      return ev;
    },
    enabled: !!id,
  });
}
