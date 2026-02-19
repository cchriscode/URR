"use client";

import { useEffect, useRef, useState, useCallback } from "react";
import { useServerTime, getServerNow } from "./use-server-time";

export interface CountdownTime {
  months: number;
  days: number;
  hours: number;
  minutes: number;
  seconds: number;
  totalDays: number;
  isExpired: boolean;
}

const ZERO: CountdownTime = {
  months: 0,
  days: 0,
  hours: 0,
  minutes: 0,
  seconds: 0,
  totalDays: 0,
  isExpired: true,
};

function computeCountdown(targetMs: number, nowMs: number): CountdownTime {
  const diff = targetMs - nowMs;
  if (diff <= 0) return ZERO;

  const totalDays = Math.floor(diff / (1000 * 60 * 60 * 24));
  const months = Math.floor(totalDays / 30);
  const days = totalDays - months * 30;
  const hours = Math.floor((diff % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60));
  const minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60));
  const seconds = Math.floor((diff % (1000 * 60)) / 1000);

  return { months, days, hours, minutes, seconds, totalDays, isExpired: false };
}

export function useCountdown(
  targetDate: string | null | undefined,
  onExpire?: () => void,
): CountdownTime {
  const { offset, ready } = useServerTime();
  const expiredRef = useRef(false);
  const onExpireRef = useRef(onExpire);
  useEffect(() => {
    onExpireRef.current = onExpire;
  });

  const getTimeLeft = useCallback((): CountdownTime => {
    if (!targetDate) return ZERO;
    const targetMs = new Date(targetDate).getTime();
    if (isNaN(targetMs)) return ZERO;
    return computeCountdown(targetMs, getServerNow(offset));
  }, [targetDate, offset]);

  const [timeLeft, setTimeLeft] = useState<CountdownTime>(() => getTimeLeft());

  useEffect(() => {
    expiredRef.current = false;
  }, [targetDate]);

  useEffect(() => {
    if (!targetDate || !ready) return;

    // If already expired at mount/effect time, mark it and skip the interval.
    // onExpire should only fire on a live active→expired transition while the
    // user is on the page — NOT on remount with stale data.  This prevents an
    // infinite loop: onExpire→fetchEvents→remount→onExpire→…
    const initial = getTimeLeft();
    if (initial.isExpired) {
      expiredRef.current = true;
      setTimeLeft(initial);
      return;
    }

    const tick = () => {
      const tl = getTimeLeft();
      setTimeLeft(tl);
      if (tl.isExpired && !expiredRef.current) {
        expiredRef.current = true;
        setTimeout(() => onExpireRef.current?.(), 100);
      }
    };

    tick();
    const id = setInterval(tick, 1000);
    return () => clearInterval(id);
  }, [targetDate, ready, getTimeLeft]);

  return timeLeft;
}

export function formatCountdown(t: CountdownTime, showMonths = false): string {
  if (t.isExpired) return "만료됨";
  const parts: string[] = [];
  if (showMonths && t.months > 0) parts.push(`${t.months}개월`);
  if (showMonths ? t.days > 0 : t.totalDays > 0) {
    parts.push(`${showMonths ? t.days : t.totalDays}일`);
  }
  if (t.hours > 0 || parts.length > 0) parts.push(`${t.hours}시간`);
  parts.push(`${t.minutes}분`);
  parts.push(`${t.seconds}초`);
  return parts.join(" ");
}

export function formatCountdownShort(t: CountdownTime): string {
  if (t.isExpired) return "00:00";
  const totalMin = t.totalDays * 24 * 60 + t.hours * 60 + t.minutes;
  return `${String(totalMin).padStart(2, "0")}:${String(t.seconds).padStart(2, "0")}`;
}
