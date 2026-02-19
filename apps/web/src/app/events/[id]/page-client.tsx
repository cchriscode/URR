"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useEventDetail } from "@/hooks/use-events";
import { Spinner } from "@/components/ui/Spinner";
import EventDetailClient from "./event-detail-client";
import type { EventDetail } from "@/lib/types";

interface EventDetailPageClientProps {
  initialEvent: EventDetail | null;
}

export default function EventDetailPageClient({ initialEvent }: EventDetailPageClientProps) {
  const params = useParams<{ id: string }>();
  const { data: event = initialEvent, isLoading } = useEventDetail(params.id, initialEvent);

  if (isLoading && !initialEvent) {
    return (
      <div className="flex justify-center py-20">
        <Spinner size="sm" className="border-sky-500 border-t-transparent" />
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
