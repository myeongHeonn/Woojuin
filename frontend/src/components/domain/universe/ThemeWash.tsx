import { useEffect, useRef, useState } from 'react';
import { useAtomValue } from 'jotai';
import { resolvedThemeAtom, themeAtom } from '@/stores/themeAtoms';

/** 밤→낮은 여명(dawn), 낮→밤은 노을(dusk). */
type Wash = 'dawn' | 'dusk';

/**
 * 안전망일 뿐이다. 정상 경로는 animationend 로 걷어내므로 CSS 의
 * --universe-theme-duration 을 여기 옮겨 적지 않는다(옮겨 적으면 한쪽만 바뀌어 어긋난다).
 * 애니메이션이 아예 돌지 않는 경우(reduced-motion 으로 display:none)에만 이 타이머가 쓰인다.
 */
const WASH_FALLBACK_MS = 4000;

/**
 * 테마가 바뀌는 동안 스테이지 위를 한 번 훑고 지나가는 하늘빛.
 *
 * 색만 A에서 B로 바꾸면 "배경색이 갈렸다"로 읽힌다. 실제 하늘은 낮과 밤 사이에 반드시 노을을
 * 지나가므로, 그 구간을 한 번 그려 주면 같은 전환이 시간의 흐름으로 읽힌다.
 *
 * 하늘빛은 동(오른쪽)에서 들어와 서(왼쪽)로 번져 나간다 — 해가 그 방향으로 가기 때문이다.
 * 화면 전체가 동시에 물들면 조명 스위치를 누른 것으로 보인다.
 *
 * 지평선 쪽의 빛덩이(::after)는 해다. 가로로는 늘 서쪽으로 흐르고, 세로만 갈린다 —
 * 여명은 떠오르고 노을은 진다.
 *
 * 전환 중에만 존재한다. 상시 렌더해 두고 opacity 만 만지면 blur(30px) 레이어가 계속 합성에
 * 남는다.
 */
const ThemeWash = () => {
  const preference = useAtomValue(themeAtom);
  const theme = useAtomValue(resolvedThemeAtom);
  const previous = useRef({ preference, theme });
  // key 는 연타 대응이다 — 같은 방향으로 다시 눌렀을 때 애니메이션을 처음부터 다시 돌린다
  const [wash, setWash] = useState<{ kind: Wash; key: number } | null>(null);

  useEffect(() => {
    const before = previous.current;
    previous.current = { preference, theme };
    if (before.theme === theme) return;

    /*
     * 현재시간 모드에서 **시간이 흘러** 낮/밤이 갈린 경우에는 훑지 않는다.
     *
     * 그 화면은 이미 실제 하늘이 스스로 물들고 있다. 위에 한 번 더 훑으면 사용자는 아무것도
     * 안 했는데 화면이 갑자기 쓸려 지나가는 것으로 보인다. 훑기는 **누른 것에 대한 응답**이라
     * 누르지 않았는데 나오면 놀랄 일이 된다.
     *
     * 설정을 바꿔서 넘어온 경우는(밤 → 현재시간 같은) 사용자가 누른 것이므로 그대로 훑는다.
     * 그래서 테마가 아니라 **설정이 그대로였는지**로 가른다.
     */
    if (before.preference === 'auto' && preference === 'auto') return;

    setWash({ kind: theme === 'light' ? 'dawn' : 'dusk', key: Date.now() });
    const timer = setTimeout(() => setWash(null), WASH_FALLBACK_MS);
    return () => clearTimeout(timer);
  }, [preference, theme]);

  if (!wash) return null;

  return (
    <div
      key={wash.key}
      aria-hidden="true"
      // 애니메이션은 ::before(하늘빛 띠)와 ::after(해)에 걸려 있는데, 의사요소의
      // animationend 도 이 요소로 올라온다. 둘의 길이가 같아 사실상 함께 끝난다.
      onAnimationEnd={() => setWash(null)}
      className={`woojuin-theme-wash woojuin-theme-wash-${wash.kind}`}
    />
  );
};

export default ThemeWash;
