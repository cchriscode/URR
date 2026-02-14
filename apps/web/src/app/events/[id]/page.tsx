"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useEventDetail } from "@/hooks/use-events";
import EventDetailClient from "./event-detail-client";

export default function EventDetailPage() {
  const params = useParams<{ id: string }>();
  const { data: event, isLoading } = useEventDetail(params.id);

  if (isLoading) {
    return (
      <div className="flex justify-center py-20">
        <div className="inline-block h-6 w-6 animate-spin rounded-full border-2 border-sky-500 border-t-transparent" />
      </div>
    );
  }

  if (!event) {
    return (
      <div className="max-w-2xl mx-auto text-center py-20">
        <p className="text-slate-400">이벤트를 찾을 수 없습니다.</p>
        <Link href="/" className="mt-4 inline-block text-sm text-sky-500 hover:text-sky-600">홈으로 돌아가기</Link>
      </div>
    );
  }

  return <EventDetailClient event={event} />;
}
