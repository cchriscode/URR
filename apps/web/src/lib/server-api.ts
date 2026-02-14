const BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:3001";

export async function serverFetch<T>(path: string, options?: { revalidate?: number }): Promise<T | null> {
  try {
    const res = await fetch(`${BASE_URL}/api/v1${path}`, {
      next: { revalidate: options?.revalidate ?? 60 },
    });
    if (!res.ok) return null;
    const json = await res.json();
    return json as T;
  } catch {
    return null;
  }
}
