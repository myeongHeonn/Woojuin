import { useEffect, useRef, useState } from 'react';
import { useAtomValue } from 'jotai';
import { themeAtom } from '@/stores/themeAtoms';

/** 밤→낮은 여명(dawn), 낮→밤은 노을(dusk). */
type Wash = 'dawn' | 'dusk';

/** CSS 애니메이션 길이(1.65s)와 맞춘다 — 끝나면 DOM 에서 뺀다. */
const WASH_MS = 1650;

/**
 * 테마가 바뀌는 동안 스테이지 위를 한 번 훑고 지나가는 하늘빛.
 *
 * 색만 A에서 B로 바꾸면 "배경색이 갈렸다"로 읽힌다. 실제 하늘은 낮과 밤 사이에 반드시 노을을
 * 지나가므로, 그 구간을 한 번 그려 주면 같은 전환이 시간의 흐름으로 읽힌다.
 *
 * 지평선 쪽에 해가 뜨고 지는 빛덩이(::after)를 따로 두고 서로 반대로 움직인다 —
 * 여명은 아래에서 올라오고 노을은 위에서 내려간다.
 *
 * 전환 중에만 존재한다. 상시 렌더해 두고 opacity 만 만지면 blur(30px) 레이어가 계속 합성에
 * 남는다.
 */
const ThemeWash = () => {
  const theme = useAtomValue(themeAtom);
  const previousTheme = useRef(theme);
  // key 는 연타 대응이다 — 같은 방향으로 다시 눌렀을 때 애니메이션을 처음부터 다시 돌린다
  const [wash, setWash] = useState<{ kind: Wash; key: number } | null>(null);

  useEffect(() => {
    if (previousTheme.current === theme) return;
    previousTheme.current = theme;
    setWash({ kind: theme === 'light' ? 'dawn' : 'dusk', key: Date.now() });
    const timer = setTimeout(() => setWash(null), WASH_MS);
    return () => clearTimeout(timer);
  }, [theme]);

  if (!wash) return null;

  return (
    <div
      key={wash.key}
      aria-hidden="true"
      className={`woojuin-theme-wash woojuin-theme-wash-${wash.kind}`}
    />
  );
};

export default ThemeWash;
