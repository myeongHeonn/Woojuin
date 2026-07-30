/**
 * 모바일/데스크톱 분기점 — 값의 원본은 styles/theme.css 의 `--breakpoint-desktop` 이다.
 *
 * 여기서 숫자를 다시 적지 않고 그 CSS 변수를 읽는다. 그래야 `desktop:` 프리픽스와
 * JS 판정이 같은 값을 볼 수밖에 없다. 분기점을 옮길 일이 생기면 theme.css 한 줄만
 * 고치면 CSS·JS 가 함께 따라온다.
 *
 * 모바일 전용: `desktop:hidden` · 데스크톱 전용: `hidden desktop:flex`
 */

/** CSS 를 못 읽는 상황에서만 쓰는 값 — theme.css 와 같게 유지한다 */
const MOBILE_FALLBACK = 640;
const SIDEBAR_EXPANDED_FALLBACK = 1024;

function readBreakpoint(variable: string, fallback: number): number {
  if (typeof document === 'undefined') return fallback;

  const raw = getComputedStyle(document.documentElement).getPropertyValue(variable);
  const parsed = Number.parseFloat(raw); // '640px' → 640
  return Number.isFinite(parsed) ? parsed : fallback;
}

/**
 * 이 폭 미만이 모바일이다.
 *
 * 함수인 이유: 모듈이 스타일시트보다 먼저 평가되면 상수로는 폴백이 굳어버린다.
 * 호출 시점에 읽으면 그럴 일이 없다.
 */
export const getMobileSize = (): number => readBreakpoint('--breakpoint-desktop', MOBILE_FALLBACK);

/** 데스크톱 여부 — matchMedia 에 넣어 쓴다 */
export const getDesktopQuery = (): string => `(min-width: ${getMobileSize()}px)`;

/** 사이드바를 기본 펼침 상태로 보여줄 수 있는 최소 폭 */
export const getSidebarExpandedSize = (): number =>
  readBreakpoint('--breakpoint-sidebar-expanded', SIDEBAR_EXPANDED_FALLBACK);

/** 데스크톱 셸 안에서 사이드바를 먼저 접어야 하는 폭 */
export const getSidebarCompactQuery = (): string =>
  `(max-width: ${getSidebarExpandedSize() - 1}px)`;
