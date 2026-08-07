import { atomWithStorage } from 'jotai/utils';
import { jotaiStore } from '@/stores/jotaiStore';
import { applyTheme, nowAtom, resolvedThemeAtom, themeAtom, type Theme } from '@/stores/themeAtoms';

/**
 * 마지막으로 적용된 테마. index.html 의 부팅 스크립트가 이 키를 읽는다.
 *
 * 왜 따로 두나: 저장된 값이 'auto' 면 부팅 스크립트만으로는 지금이 낮인지 밤인지 알 수 없다.
 * 태양 고도 계산을 head 인라인 스크립트에 넣을 수는 없으니, 직전에 판정한 결과를 남겨 둔다.
 * 하늘은 1분 만에 낮에서 밤이 되지 않으므로 직전 값이면 충분하고, 번들이 뜨는 즉시 다시
 * 계산해 바로잡는다.
 */
export const RESOLVED_THEME_STORAGE_KEY = 'woojuin:theme-resolved';

const resolvedThemeCacheAtom = atomWithStorage<Theme>(
  RESOLVED_THEME_STORAGE_KEY,
  'dark',
  undefined,
  { getOnInit: true },
);

/** 하늘색을 다시 계산하는 주기. 태양은 1분에 약 0.25° 움직여 그 사이 색이 눈에 띄게 변하지 않는다. */
const TICK_MS = 60_000;

/**
 * 테마를 문서에 붙이고, auto 일 때 시각을 흘려보낸다.
 *
 * React 밖에서 도는 이유: <html> 전체에 걸리는 것이라 특정 컴포넌트의 생명주기에 묶을 이유가
 * 없고, 라우팅으로 레이아웃이 갈아끼워져도 끊기면 안 된다.
 */
export function startThemeRuntime(): void {
  const sync = () => {
    const theme = jotaiStore.get(resolvedThemeAtom);
    applyTheme(theme);
    jotaiStore.set(resolvedThemeCacheAtom, theme);
  };

  sync();
  jotaiStore.sub(resolvedThemeAtom, sync);

  // auto 로 막 바꾼 순간에는 시계를 당겨 둔다 — 안 그러면 다음 tick 까지 최대 1분간
  // 예전 시각으로 계산된 하늘이 걸려 있다.
  jotaiStore.sub(themeAtom, () => {
    if (jotaiStore.get(themeAtom) === 'auto') jotaiStore.set(nowAtom, Date.now());
  });

  setInterval(() => {
    // auto 가 아니면 시각이 바뀌어도 화면에 쓰이지 않는다 — 괜한 재계산을 만들지 않는다
    if (jotaiStore.get(themeAtom) !== 'auto') return;
    jotaiStore.set(nowAtom, Date.now());
  }, TICK_MS);
}
