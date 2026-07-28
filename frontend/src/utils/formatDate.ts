/**
 * ISO 날짜 문자열 → 사람이 읽는 상대 시간.
 *   1분 미만 "방금 전" · N분 전 · N시간 전 · 7일 미만 N일 전 · 그 이상 "YYYY.MM.DD"
 * 값이 없거나 못 읽으면 빈 문자열(호출부에서 자리 자체를 비운다).
 */
export function formatRelativeDate(iso: string | null | undefined): string {
  if (!iso) return '';
  const date = new Date(iso);
  const then = date.getTime();
  if (Number.isNaN(then)) return '';

  const diffMin = Math.floor((Date.now() - then) / 60000);
  if (diffMin < 1) return '방금 전';
  if (diffMin < 60) return `${diffMin}분 전`;

  const diffHour = Math.floor(diffMin / 60);
  if (diffHour < 24) return `${diffHour}시간 전`;

  const diffDay = Math.floor(diffHour / 24);
  if (diffDay < 7) return `${diffDay}일 전`;

  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}.${pad(date.getMonth() + 1)}.${pad(date.getDate())}`;
}
