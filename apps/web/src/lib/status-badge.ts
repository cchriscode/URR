export function statusBadge(status?: string): { text: string; cls: string } {
  switch (status) {
    // Event statuses
    case "on_sale":
      return { text: "예매 중", cls: "bg-sky-50 text-sky-600" };
    case "upcoming":
      return { text: "오픈 예정", cls: "bg-amber-50 text-amber-600" };
    case "ended":
      return { text: "종료", cls: "bg-slate-100 text-slate-500" };
    case "sold_out":
      return { text: "매진", cls: "bg-red-50 text-red-500" };
    // Reservation statuses
    case "confirmed":
    case "completed":
      return { text: "확정", cls: "bg-sky-50 text-sky-600" };
    case "pending":
    case "waiting":
      return { text: "대기", cls: "bg-amber-50 text-amber-600" };
    case "cancelled":
      return { text: "취소", cls: "bg-red-50 text-red-500" };
    case "refunded":
      return { text: "환불", cls: "bg-slate-100 text-slate-500" };
    default:
      return { text: status ?? "대기", cls: "bg-slate-100 text-slate-500" };
  }
}
