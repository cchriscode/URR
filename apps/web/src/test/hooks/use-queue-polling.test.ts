import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { useQueuePolling } from '@/hooks/use-queue-polling';

// Mock the API client
vi.mock('@/lib/api-client', () => ({
  queueApi: {
    status: vi.fn(),
  },
}));

import { queueApi } from '@/lib/api-client';

describe('useQueuePolling', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('returns initial state when disabled', () => {
    const { result } = renderHook(() => useQueuePolling('event-1', false));
    expect(result.current.status).toBeNull();
    expect(result.current.loading).toBe(false);
    expect(result.current.error).toBeNull();
  });

  it('returns initial state for empty eventId', () => {
    const { result } = renderHook(() => useQueuePolling('', true));
    expect(result.current.status).toBeNull();
    expect(result.current.loading).toBe(false);
  });

  it('polls queue status on mount', async () => {
    const mockData = {
      status: 'waiting',
      position: 42,
      totalAhead: 100,
      estimatedWaitSeconds: 60,
      nextPoll: 3,
    };
    vi.mocked(queueApi.status).mockResolvedValue({ data: mockData } as never);

    const { result } = renderHook(() => useQueuePolling('event-1', true));

    await waitFor(() => {
      expect(result.current.status).not.toBeNull();
    }, { timeout: 3000 });

    expect(result.current.status?.position).toBe(42);
    expect(result.current.error).toBeNull();
    expect(queueApi.status).toHaveBeenCalledWith('event-1');
  });

  it('sets error on API failure', async () => {
    vi.mocked(queueApi.status).mockRejectedValue(new Error('Network error'));

    const { result } = renderHook(() => useQueuePolling('event-1', true));

    await waitFor(() => {
      expect(result.current.error).toBe('Queue status polling failed');
    }, { timeout: 3000 });
  });
});
