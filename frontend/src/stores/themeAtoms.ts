import { atom } from 'jotai';
import { atomWithStorage } from 'jotai/utils';
import { approximateCoordinates, isDaylight, solarElevation } from '@/utils/solar';

/** 사용자가 고르는 값. auto 는 지금 시각의 실제 하늘을 따라간다. */
export type ThemePreference = 'dark' | 'light' | 'auto';
/** 화면에 실제로 적용되는 값 — auto 는 낮/밤 둘 중 하나로 풀린다. */
export type Theme = 'dark' | 'light';

/**
 * 저장 키. index.html 의 부팅 스크립트가 **같은 키를 직접 읽으므로** 바꾸면 그쪽도 같이 바꾼다.
 * (부팅 스크립트가 필요한 이유는 아래 themeAtom 주석 참고)
 */
export const THEME_STORAGE_KEY = 'woojuin:theme';

/**
 * 화면 테마.
 *
 * 기본은 **다크**다 — 우주 스테이지가 제품의 정체성이고, 나머지는 사용자가 직접 골랐을 때만
 * 쓴다. 시스템 설정(prefers-color-scheme)을 따라가지 않는 것도 같은 이유다.
 *
 * getOnInit: true 인 이유는 authAtoms 와 같다. 없으면 첫 렌더가 initialValue('dark')로 시작해
 * 다른 테마를 켜 둔 사용자가 새로고침할 때마다 다크로 한 번 깜빡인다.
 */
export const themeAtom = atomWithStorage<ThemePreference>(THEME_STORAGE_KEY, 'dark', undefined, {
  getOnInit: true,
});

/**
 * 지금 시각. auto 일 때만 의미가 있고, 아래 resolvedThemeAtom 과 하늘색이 이 값을 따라 다시
 * 계산된다. 갱신은 themeRuntime 이 맡는다.
 *
 * 시계를 atom 으로 두는 이유: 하늘색과 UI 밝기가 같은 시각을 봐야 한다. 각자 Date.now() 를
 * 부르면 몇 밀리초 차이로 서로 다른 판단을 할 수 있다.
 */
export const nowAtom = atom<number>(Date.now());

/** 지금 태양 고도(도). auto 가 아니면 화면에 쓰이지 않는다. */
export const solarElevationAtom = atom((get) => {
  const at = new Date(get(nowAtom));
  return solarElevation(at, approximateCoordinates(at));
});

/**
 * 실제로 문서에 붙는 테마.
 *
 * auto 는 태양 고도로 낮/밤을 가른다 — 하늘색은 연속으로 변하지만 버튼·글자는 그럴 수 없다.
 * 대비가 보장돼야 읽히기 때문에 UI 만큼은 한 지점에서 끊는다.
 */
export const resolvedThemeAtom = atom<Theme>((get) => {
  const preference = get(themeAtom);
  if (preference !== 'auto') return preference;
  return isDaylight(get(solarElevationAtom)) ? 'light' : 'dark';
});

/** 브라우저 UI 바 색. theme.css 의 --color-space 와 같은 값이어야 앱 배경과 이어져 보인다. */
const THEME_COLORS: Record<Theme, string> = {
  dark: '#0e1017',
  light: '#f4f5f8',
};

/**
 * 테마를 문서에 반영한다. theme.css 가 `:root[data-theme='light']` 로 색 토큰을 덮어쓰므로
 * 이 속성 하나만 바꾸면 화면 전체가 따라온다.
 *
 * theme-color 메타도 같이 바꾼다 — 안 바꾸면 라이트 모드에서 모바일 주소창만 어두운 채로
 * 남아 앱 배경과 끊겨 보인다.
 */
export function applyTheme(theme: Theme): void {
  document.documentElement.dataset.theme = theme;
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', THEME_COLORS[theme]);
}
