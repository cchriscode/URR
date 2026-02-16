import ArtistsPageClient from "./page-client";

async function getArtists() {
  try {
    const res = await fetch(`${process.env.INTERNAL_API_URL || 'http://gateway-service:3001'}/api/v1/artists?page=1&limit=50`, {
      next: { revalidate: 60 },
    });
    if (!res.ok) return [];
    const data = await res.json();
    return data.artists ?? data.data ?? [];
  } catch {
    return [];
  }
}

export default async function ArtistsPage() {
  const artists = await getArtists();
  return <ArtistsPageClient initialArtists={artists} />;
}
