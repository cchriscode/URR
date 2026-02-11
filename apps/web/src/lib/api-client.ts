"use client";

import axios from "axios";
import { clearAuth, getToken } from "@/lib/storage";
import type { QueueStatus } from "@/lib/types";

function resolveBaseUrl() {
  if (process.env.NEXT_PUBLIC_API_URL) {
    return process.env.NEXT_PUBLIC_API_URL;
  }
  if (typeof window === "undefined") {
    return "http://localhost:3001";
  }

  const hostname = window.location.hostname;
  if (hostname === "localhost" || hostname === "127.0.0.1") {
    return "http://localhost:3001";
  }
  if (/^(172\.|192\.168\.|10\.)/.test(hostname)) {
    return `http://${hostname}:3001`;
  }
  return "";
}

const http = axios.create({
  baseURL: `${resolveBaseUrl()}/api`,
  headers: { "Content-Type": "application/json" },
});

http.interceptors.request.use((config) => {
  const token = getToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

http.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      clearAuth();
      if (typeof window !== "undefined") {
        window.location.href = "/login";
      }
    }
    return Promise.reject(error);
  }
);

export const authApi = {
  register: (payload: { email: string; password: string; name: string; phone?: string }) =>
    http.post("/auth/register", payload),
  login: (payload: { email: string; password: string }) => http.post("/auth/login", payload),
  me: () => http.get("/auth/me"),
};

export const eventsApi = {
  list: (params?: Record<string, string | number>) => http.get("/events", { params }),
  detail: (id: string) => http.get(`/events/${id}`),
};

export const queueApi = {
  check: (eventId: string) => http.post<QueueStatus>(`/queue/check/${eventId}`),
  status: (eventId: string) => http.get<QueueStatus>(`/queue/status/${eventId}`),
  heartbeat: (eventId: string) => http.post(`/queue/heartbeat/${eventId}`),
  leave: (eventId: string) => http.post(`/queue/leave/${eventId}`),
};

export const reservationsApi = {
  // For standing / ticket-type-only events (no seat selection)
  createTicketOnly: (payload: {
    eventId: string;
    items: Array<{ ticketTypeId: string; quantity: number }>;
  }) => http.post("/reservations", payload),
  mine: () => http.get("/reservations/my"),
  byId: (id: string) => http.get(`/reservations/${id}`),
  cancel: (id: string) => http.post(`/reservations/${id}/cancel`),
};

export const seatsApi = {
  byEvent: (eventId: string) => http.get(`/seats/events/${eventId}`),
  reserve: (payload: { eventId: string; seatIds: string[] }) =>
    http.post("/seats/reserve", payload),
};

export const paymentsApi = {
  prepare: (payload: Record<string, unknown>) => http.post("/payments/prepare", payload),
  confirm: (payload: Record<string, unknown>) => http.post("/payments/confirm", payload),
  process: (payload: Record<string, unknown>) => http.post("/payments/process", payload),
};

export const statsApi = {
  overview: () => http.get("/stats/overview"),
  daily: (days?: number) => http.get("/stats/daily", { params: { days: days ?? 30 } }),
  events: (params?: { limit?: number; sortBy?: string }) => http.get("/stats/events", { params }),
  eventDetail: (eventId: string) => http.get(`/stats/events/${eventId}`),
  payments: () => http.get("/stats/payments"),
  revenue: (params?: { period?: string; days?: number }) => http.get("/stats/revenue", { params }),
  users: (days?: number) => http.get("/stats/users", { params: { days: days ?? 30 } }),
  hourlyTraffic: (days?: number) => http.get("/stats/hourly-traffic", { params: { days: days ?? 7 } }),
  conversion: (days?: number) => http.get("/stats/conversion", { params: { days: days ?? 30 } }),
  cancellations: (days?: number) => http.get("/stats/cancellations", { params: { days: days ?? 30 } }),
  realtime: () => http.get("/stats/realtime"),
  performance: () => http.get("/stats/performance"),
  seatPreferences: (eventId?: string) => http.get("/stats/seat-preferences", { params: eventId ? { eventId } : {} }),
  userBehavior: (days?: number) => http.get("/stats/user-behavior", { params: { days: days ?? 30 } }),
};

export const newsApi = {
  list: () => http.get("/news"),
  byId: (id: string) => http.get(`/news/${id}`),
  create: (payload: { title: string; content: string; author: string; author_id?: string; is_pinned?: boolean }) =>
    http.post("/news", payload),
  update: (id: string, payload: { title: string; content: string; is_pinned?: boolean }) =>
    http.put(`/news/${id}`, payload),
  delete: (id: string) => http.delete(`/news/${id}`),
};

export const artistsApi = {
  list: (params?: Record<string, string | number>) => http.get("/artists", { params }),
  detail: (id: string) => http.get(`/artists/${id}`),
};

export const membershipsApi = {
  subscribe: (artistId: string) => http.post("/memberships/subscribe", { artistId }),
  my: () => http.get("/memberships/my"),
  myForArtist: (artistId: string) => http.get(`/memberships/my/${artistId}`),
  benefits: (artistId: string) => http.get(`/memberships/benefits/${artistId}`),
};

export const transfersApi = {
  list: (params?: Record<string, string | number>) => http.get("/transfers", { params }),
  my: () => http.get("/transfers/my"),
  detail: (id: string) => http.get(`/transfers/${id}`),
  create: (reservationId: string) => http.post("/transfers", { reservationId }),
  cancel: (id: string) => http.post(`/transfers/${id}/cancel`),
};

export const adminApi = {
  dashboard: () => http.get("/admin/dashboard"),
  seatLayouts: () => http.get("/admin/seat-layouts"),
  events: {
    create: (payload: Record<string, unknown>) => http.post("/admin/events", payload),
    update: (id: string, payload: Record<string, unknown>) => http.put(`/admin/events/${id}`, payload),
    cancel: (id: string) => http.post(`/admin/events/${id}/cancel`),
    remove: (id: string) => http.delete(`/admin/events/${id}`),
    generateSeats: (id: string) => http.post(`/admin/events/${id}/generate-seats`),
    clearSeats: (id: string) => http.delete(`/admin/events/${id}/seats`),
  },
  tickets: {
    create: (eventId: string, payload: Record<string, unknown>) =>
      http.post(`/admin/events/${eventId}/tickets`, payload),
    update: (id: string, payload: Record<string, unknown>) =>
      http.put(`/admin/tickets/${id}`, payload),
  },
  reservations: {
    list: (params?: Record<string, string | number>) => http.get("/admin/reservations", { params }),
    updateStatus: (id: string, status: string) =>
      http.patch(`/admin/reservations/${id}/status`, { status }),
  },
};
