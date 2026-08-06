import { atomWithStorage } from 'jotai/utils';

export type Theme = 'dark' | 'light';

/**
 * 저장 키. index.html 의 부팅 스크립트가 **같은 키를 직접 읽으므로** 바꾸면 그쪽도 같이 바꾼다.
 * (부팅 스크립트가 필요한 이유는 아래 themeAtom 주석 참고)
 */
export const THEME_STORAGE_KEY = 'woojuin:theme';

/**
 * 화면 테마.
 *
 * 기본은 **다크**다 — 우주 스테이지가 제품의 정체성이고, 라이트는 사용자가 설정에서 직접 켰을
 * 때만 쓴다. 시스템 설정(prefers-color-scheme)을 따라가지 않는 것도 같은 이유다: 아직 라이트로
 * 검증되지 않은 화면이 남아 있어, OS 설정만으로 기본 노출되면 안 된다.
 *
 * getOnInit: true 인 이유는 authAtoms 와 같다. 없으면 첫 렌더가 initialValue('dark')로 시작해
 * 라이트를 켜 둔 사용자가 새로고침할 때마다 다크로 한 번 깜빡인다.
 */
export const themeAtom = atomWithStorage<Theme>(THEME_STORAGE_KEY, 'dark', undefined, {
  getOnInit: true,
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
