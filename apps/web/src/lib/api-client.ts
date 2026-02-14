"use client";

import axios from "axios";
import type { AxiosRequestConfig } from "axios";
import { clearAuth } from "@/lib/storage";
import type { QueueStatus } from "@/lib/types";

function resolveBaseUrl() {
  // Build-time env var (baked into JS bundle by Next.js)
  if (process.env.NEXT_PUBLIC_API_URL) {
    return process.env.NEXT_PUBLIC_API_URL;
  }

  // SSR fallback (server-side rendering without env var)
  if (typeof window === "undefined") {
    return "http://localhost:3001";
  }

  // Runtime: check if window.__API_URL is injected (K8s environments)
  const w = window as unknown as Record<string, unknown>;
  if (typeof w.__API_URL === "string" && w.__API_URL) {
    return w.__API_URL;
  }

  // Local development: auto-detect gateway port
  const hostname = window.location.hostname;
  if (hostname === "localhost" || hostname === "127.0.0.1") {
    return "http://localhost:3001";
  }
  if (/^(172\.|192\.168\.|10\.)/.test(hostname)) {
    return `http://${hostname}:3001`;
  }

  // Production: same-origin relative path (CloudFront/ALB reverse proxy)
  return "";
}

const http = axios.create({
  baseURL: `${resolveBaseUrl()}/api/v1`,
  headers: { "Content-Type": "application/json" },
  withCredentials: true,
  timeout: 15000,
});

function getCookie(name: string): string | null {
  if (typeof document === "undefined") return null;
  const match = document.cookie.match(new RegExp(`(?:^|; )${name}=([^;]*)`));
  return match ? decodeURIComponent(match[1]) : null;
}

// --- Silent refresh logic ---
let isRefreshing = false;
let failedQueue: Array<{
  resolve: (value: unknown) => void;
  reject: (reason: unknown) => void;
  config: AxiosRequestConfig;
}> = [];

function processQueue(success: boolean) {
  failedQueue.forEach(({ resolve, reject, config }) => {
    if (success) {
      resolve(http(config));
    } else {
      reject(new Error("Session expired"));
    }
  });
  failedQueue = [];
}

http.interceptors.request.use((config) => {
  // Attach queue entry token for Lambda@Edge and Gateway VWR verification
  const entryToken = getCookie("tiketi-entry-token");
  if (entryToken) {
    config.headers["x-queue-entry-token"] = entryToken;
  }
  return config;
});

http.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    // Only attempt refresh on 401 and not already retried
    if (error.response?.status === 401 && !originalRequest._retry) {
      // Don't attempt refresh for auth endpoints themselves
      const url = originalRequest.url || "";
      if (url.includes("/auth/login") || url.includes("/auth/register") || url.includes("/auth/refresh")) {
        return Promise.reject(error);
      }

      if (isRefreshing) {
        // Queue this request while refresh is in progress
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject, config: originalRequest });
        });
      }

      originalRequest._retry = true;
      isRefreshing = true;

      try {
        await http.post("/auth/refresh");
        processQueue(true);
        // Retry the original request (new cookie is set automatically)
        return http(originalRequest);
      } catch {
        processQueue(false);
        clearAuth();
        // Don't redirect here — AuthGuard handles redirects for protected pages.
        // Redirecting here causes infinite reload loops on /login.
        return Promise.reject(error);
      } finally {
        isRefreshing = false;
      }
    }

    // 429 Too Many Requests: retry with exponential backoff (up to 2 retries)
    if (error.response?.status === 429) {
      const retryCount = originalRequest._retryCount ?? 0;
      if (retryCount < 2) {
        originalRequest._retryCount = retryCount + 1;
        const delay = Math.min(1000 * Math.pow(2, retryCount), 4000);
        await new Promise((r) => setTimeout(r, delay));
        return http(originalRequest);
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
  refresh: () => http.post("/auth/refresh"),
  logout: () => http.post("/auth/logout"),
  google: (credential: string) => http.post("/auth/google", { credential }),
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
    idempotencyKey?: string;
  }) => http.post("/reservations", {
    ...payload,
    idempotencyKey: payload.idempotencyKey ?? crypto.randomUUID(),
  }),
  mine: () => http.get("/reservations/my"),
  byId: (id: string) => http.get(`/reservations/${id}`),
  cancel: (id: string) => http.post(`/reservations/${id}/cancel`),
};

export const seatsApi = {
  byEvent: (eventId: string) => http.get(`/seats/events/${eventId}`),
  reserve: (payload: { eventId: string; seatIds: string[]; idempotencyKey?: string }) =>
    http.post("/seats/reserve", {
      ...payload,
      idempotencyKey: payload.idempotencyKey ?? crypto.randomUUID(),
    }),
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

export const communityApi = {
  posts: (params?: Record<string, string | number>) => http.get("/community/posts", { params }),
  postDetail: (id: string) => http.get(`/community/posts/${id}`),
  createPost: (payload: { title: string; content: string; artist_id: string }) =>
    http.post("/community/posts", payload),
  updatePost: (id: string, payload: { title: string; content: string }) =>
    http.put(`/community/posts/${id}`, payload),
  deletePost: (id: string) => http.delete(`/community/posts/${id}`),
  comments: (postId: string, params?: Record<string, string | number>) =>
    http.get(`/community/posts/${postId}/comments`, { params }),
  createComment: (postId: string, payload: { content: string }) =>
    http.post(`/community/posts/${postId}/comments`, payload),
  deleteComment: (postId: string, commentId: string) =>
    http.delete(`/community/posts/${postId}/comments/${commentId}`),
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
