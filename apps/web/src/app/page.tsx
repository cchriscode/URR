import { HomePageClient } from "./page-client";

async function getEvents() {
  try {
    const res = await fetch(`${process.env.INTERNAL_API_URL || 'http://gateway-service:3001'}/api/v1/events?status=on_sale&page=1&limit=12`, {
      next: { revalidate: 60 },
    });
    if (!res.ok) return [];
    const data = await res.json();
    return data.events ?? data.data ?? [];
  } catch {
    return [];
  }
}

export default async function HomePage() {
  const events = await getEvents();
  return <HomePageClient initialEvents={events} />;
}
