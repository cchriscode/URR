import EventDetailPageClient from "./page-client";

async function getEvent(id: string) {
  try {
    const res = await fetch(`${process.env.INTERNAL_API_URL || 'http://gateway-service:3001'}/api/v1/events/${id}`, {
      next: { revalidate: 60 },
    });
    if (!res.ok) return null;
    const data = await res.json();
    return data.event ?? data.data ?? data ?? null;
  } catch {
    return null;
  }
}

export default async function EventDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const event = await getEvent(id);
  return <EventDetailPageClient initialEvent={event} />;
}
