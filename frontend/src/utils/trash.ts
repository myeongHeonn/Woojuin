/** 휴지통 항목은 삭제(deletedAt) 후 이 일수가 지나면 자동으로 영구 삭제된다 */
export const TRASH_PURGE_DAYS = 30;

/**
 * 자동 삭제까지 남은 일수 — D-day 표시용. deletedAt 이 없거나 못 읽으면 null.
 * 이미 지났으면 0(= D-0). 하루 단위로 올림한다(오늘 하루도 남은 것으로 센다).
 */
export function daysUntilPurge(deletedAt: string | null | undefined): number | null {
  if (!deletedAt) return null;
  const deleted = new Date(deletedAt).getTime();
  if (Number.isNaN(deleted)) return null;

  const purgeAt = deleted + TRASH_PURGE_DAYS * 24 * 60 * 60 * 1000;
  const remainingDays = Math.ceil((purgeAt - Date.now()) / (24 * 60 * 60 * 1000));
  return Math.max(0, remainingDays);
}
