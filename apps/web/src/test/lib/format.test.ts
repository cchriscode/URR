import { describe, it, expect } from 'vitest';
import { formatEventDate, formatPrice, formatDate, formatDateTime } from '@/lib/format';

describe('formatEventDate', () => {
  it('returns "-" for null/undefined', () => {
    expect(formatEventDate(null)).toBe('-');
    expect(formatEventDate(undefined)).toBe('-');
  });

  it('formats a valid ISO date string', () => {
    // Use a fixed timezone offset to make test deterministic
    const result = formatEventDate('2026-03-14T19:00:00.000+09:00');
    expect(result).toMatch(/2026년 3월 14일/);
    expect(result).toMatch(/\(토\)/);
  });

  it('returns original string for invalid date', () => {
    expect(formatEventDate('not-a-date')).toBe('not-a-date');
  });
});

describe('formatPrice', () => {
  it('formats Korean won amounts', () => {
    expect(formatPrice(50000)).toBe('50,000');
    expect(formatPrice(150000)).toBe('150,000');
    expect(formatPrice(0)).toBe('0');
  });
});

describe('formatDate', () => {
  it('returns empty string for undefined', () => {
    expect(formatDate(undefined)).toBe('');
  });

  it('formats date in Korean locale', () => {
    const result = formatDate('2026-01-15');
    expect(result).toMatch(/2026/);
    expect(result).toMatch(/1/);
    expect(result).toMatch(/15/);
  });
});

describe('formatDateTime', () => {
  it('returns empty string for undefined', () => {
    expect(formatDateTime(undefined)).toBe('');
  });

  it('formats datetime in Korean locale', () => {
    const result = formatDateTime('2026-03-14T19:00:00Z');
    expect(result).toMatch(/2026/);
  });
});
