const DAY_NAMES = ["일", "월", "화", "수", "목", "금", "토"];

export function formatEventDate(d?: string | null): string {
  if (!d) return "-";
  try {
    const date = new Date(d);
    if (isNaN(date.getTime())) return d;
    const y = date.getFullYear();
    const m = date.getMonth() + 1;
    const day = date.getDate();
    const dow = DAY_NAMES[date.getDay()];
    const hh = String(date.getHours()).padStart(2, "0");
    const mm = String(date.getMinutes()).padStart(2, "0");
    return `${y}년 ${m}월 ${day}일 (${dow}) ${hh}:${mm}`;
  } catch {
    return d ?? "-";
  }
}

export function formatPrice(price: number): string {
  return new Intl.NumberFormat("ko-KR").format(price);
}
