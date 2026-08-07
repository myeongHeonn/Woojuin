import { afterEach, beforeEach, vi } from 'vitest';
import { api } from '@/services/client';

type ApiMethod = 'get' | 'post' | 'patch' | 'put' | 'delete';
type MutableApi = Record<ApiMethod, unknown>;

/**
 * `api.<method>` 를 vi.fn() 으로 갈아끼우고 테스트마다 초기화·복원한다.
 *
 * **왜 `vi.mock('@/services/client')` 을 쓰지 않는가** — 2026-08-03 CI 간헐 실패의 결론:
 *   browser mode 의 모듈 mock 이 **적용되지 않는 실행**이 있었다. 그러면 테스트가 실제
 *   axios 인스턴스를 받게 되고, 그 메서드는 mock 이 아니므로
 *   `patch.mockReset is not a function` 으로 죽는다. (같은 커밋 재실행하면 통과 →
 *   코드가 아니라 실행 조건 문제라는 증거.)
 *
 *   실험으로 좁힌 결과:
 *     - `__mocks__` 자동 탐색이 실패해 automock 으로 넘어가는 경우 → **정상 mock** 이
 *       만들어져 통과한다(대조 실험에서 확인). 즉 그 경로는 원인이 아니다.
 *     - `vi.mock` 자체가 적용되지 않은 상태 → CI 와 **똑같은 에러 문구**가 재현된다.
 *   그래서 근본 대책은 "모듈 mock 을 더 잘 쓰는 것"이 아니라 **모듈 mock 에 의존하지
 *   않는 것**이다. 여기서는 테스트가 실제로 받은 모듈 객체를 직접 수정하므로, mock 이
 *   적용되든 안 되든 항상 동작한다 — 간헐 실패의 조건 자체가 없어진다.
 *
 * axios 인스턴스의 메서드는 일반 속성이라 재할당이 가능하다(ESM export 바인딩과 다르다).
 * 그래서 함수 export(`requestTokenRefresh` 등)를 가로채야 하는 테스트는 이 방법을 쓸 수
 * 없고 모듈 mock 이 필요하다 — useWorkspaceEvents 테스트가 그 경우다.
 */
export function stubApi(method: ApiMethod) {
  const stub = vi.fn();
  const original = (api as unknown as MutableApi)[method];

  beforeEach(() => {
    stub.mockReset();
    (api as unknown as MutableApi)[method] = stub;
  });

  // 모듈 인스턴스가 파일 간에 공유될 수 있으므로 반드시 되돌린다.
  afterEach(() => {
    (api as unknown as MutableApi)[method] = original;
  });

  return stub;
}
