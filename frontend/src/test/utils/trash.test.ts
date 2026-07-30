import { describe, it, expect, vi, afterEach } from 'vitest';
import { daysUntilPurge, TRASH_PURGE_DAYS } from '@/utils/trash';

const DAY = 24 * 60 * 60 * 1000;

afterEach(() => vi.useRealTimers());

describe('daysUntilPurge', () => {
  it('deletedAt 이 없으면 null', () => {
    expect(daysUntilPurge(null)).toBeNull();
    expect(daysUntilPurge(undefined)).toBeNull();
  });

  it('방금 삭제됐으면 30일(=D-30) 남는다', () => {
    vi.useFakeTimers().setSystemTime(new Date('2026-07-29T00:00:00Z'));
    expect(daysUntilPurge('2026-07-29T00:00:00Z')).toBe(TRASH_PURGE_DAYS);
  });

  it('1일 지났으면 29일 남는다(D-29)', () => {
    const now = Date.now();
    vi.useFakeTimers().setSystemTime(now);
    const deletedAt = new Date(now - 1 * DAY).toISOString();
    expect(daysUntilPurge(deletedAt)).toBe(29);
  });

  it('30일이 지났으면 0(D-0)로 바닥을 친다', () => {
    const now = Date.now();
    vi.useFakeTimers().setSystemTime(now);
    const deletedAt = new Date(now - 40 * DAY).toISOString();
    expect(daysUntilPurge(deletedAt)).toBe(0);
  });
});
