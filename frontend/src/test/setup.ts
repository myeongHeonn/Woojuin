/**
 * 테스트 전역 셋업.
 * 실제 브라우저에서 돌기 때문에 디자인 토큰(theme.css)을 불러와야
 * w-sidebar·pl-1 같은 Tailwind 클래스가 실제 픽셀로 계산된다.
 */
import { beforeEach } from 'vitest';
// '@vitest/browser/context' 는 deprecated — CI 로그에 "will stop working in the next
// major version" 경고가 찍혀 공식 대체 경로(vitest/browser)로 바꿨다(vitest 4.x).
import { userEvent } from 'vitest/browser';
import '@/styles/index.css';

/**
 * 각 테스트 전에 **마우스 커서를 화면 우하단 구석으로 치운다.**
 *
 * 왜 필요한가 — 2026-07-31 CI 실패에서 온 결론:
 *   browser mode 는 실제 Chromium 이라 **실제 커서 좌표가 존재**하고, 그 좌표는 테스트와
 *   테스트 파일 사이에 그대로 남는 **전역 상태**다. 렌더된 컴포넌트가 하필 그 좌표 아래에
 *   놓이면 `:hover` 가 걸린다.
 *
 *   `NavItem` 의 접힘 툴팁은 `hidden group-hover:block` 인데, `group-hover:` 는
 *   `.group:hover .x { display:block }` 으로 컴파일돼 특이도가 `.hidden` 보다 **높다**.
 *   즉 커서가 그 위에 있으면 block 이 이겨서
 *   `expect(getComputedStyle(label).display).toBe('none')` 가 깨진다.
 *   실제로 CI 에서 `expected 'block' to be 'none'` 으로 터졌고, 같은 커밋을 다시 돌리면
 *   통과했다 — 코드가 아니라 커서 위치 문제라는 증거다.
 *
 * 컴포넌트는 대개 좌상단에 렌더되므로 커서를 우하단에 고정해 두면 겹치지 않는다.
 * 개별 테스트를 고치는 대신 여기 두는 이유: 호버·포커스에 의존하는 단정이 앞으로 늘어도
 * 같은 함정에 다시 빠지지 않게 하려고.
 *
 * ⚠️ 우하단을 덮는 UI(전체 화면 오버레이 등)의 호버 상태를 검증하려는 테스트는
 *    이 기본값을 의식하고 직접 `userEvent.hover(...)` 로 커서를 옮겨야 한다.
 */
beforeEach(async () => {
  const park = document.createElement('div');
  park.setAttribute('data-cursor-park', '');
  park.style.cssText = 'position:fixed;right:0;bottom:0;width:8px;height:8px;z-index:2147483647;';
  document.body.appendChild(park);
  try {
    await userEvent.hover(park);
  } finally {
    // 커서는 이 좌표에 남는다 — 요소를 지워도 위치는 되돌아가지 않는다.
    park.remove();
  }
});
