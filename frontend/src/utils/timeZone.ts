/**
 * 표준시 약칭(KST·JST·PDT …).
 *
 * Intl 은 약칭을 직접 주지 않는다 — timeZoneName:'short' 는 어느 로케일에서도 "GMT+9" 로
 * 떨어진다. 대신 영문 정식 명칭("Korean Standard Time")의 머리글자를 따면 실제로 쓰는 약칭과
 * 일치한다: KST·JST·EDT·CEST·AEST·IST·PDT 모두 확인했다.
 *
 * 이름이 없는 지역(정식 명칭이 "GMT+05:30" 처럼 나오는 곳)은 머리글자를 딸 수 없으므로
 * UTC 오프셋으로 떨어진다. 시계 옆에 붙는 단위라 비어 있으면 안 된다.
 */

/** 유일한 예외 — 머리글자로는 CUT 이 되는데 이건 아무도 그렇게 안 쓴다. */
const UTC_LONG_NAME = 'Coordinated Universal Time';

function longName(date: Date): string {
  return (
    new Intl.DateTimeFormat('en-US', { timeZoneName: 'long' })
      .formatToParts(date)
      .find((part) => part.type === 'timeZoneName')?.value ?? ''
  );
}

function utcOffsetLabel(date: Date): string {
  // getTimezoneOffset 은 UTC 기준이고 동쪽이 음수다
  const totalMinutes = -date.getTimezoneOffset();
  const sign = totalMinutes < 0 ? '-' : '+';
  const hours = Math.floor(Math.abs(totalMinutes) / 60);
  const minutes = Math.abs(totalMinutes) % 60;
  return `UTC${sign}${hours}${minutes ? `:${String(minutes).padStart(2, '0')}` : ''}`;
}

export function timeZoneAbbreviation(date: Date = new Date()): string {
  const name = longName(date);
  if (name === UTC_LONG_NAME) return 'UTC';

  // 글자와 공백뿐일 때만 이름으로 본다 — "GMT+05:30" 같은 값은 머리글자가 의미 없다
  if (/^[A-Za-z ]+$/.test(name)) {
    const initials = name
      .split(' ')
      .filter(Boolean)
      .map((word) => word[0])
      .join('')
      .toUpperCase();
    if (initials.length >= 2 && initials.length <= 5) return initials;
  }

  return utcOffsetLabel(date);
}
