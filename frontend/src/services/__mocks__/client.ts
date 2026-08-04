/**
 * `@/services/client` 의 **공용 mock**. 테스트는 `vi.mock('@/services/client')` 만 쓰면
 * (팩토리 없이) Vitest 가 이 파일을 대신 넣어 준다.
 *
 * 왜 파일마다 팩토리를 쓰지 않는가 — 2026-07-31 CI 실패에서 온 결론:
 *   서비스 테스트 3개가 같은 모듈을 **각기 다른 팩토리**로 mock 하고 있었다.
 *     auth={get,post,patch} / items={post,get} / universe={get}
 *   browser mode 의 모듈 mock 은 Vite 서버 쪽에서 모듈 요청을 가로채는 방식이라,
 *   같은 모듈 경로를 여러 파일이 **동시에** mock 하면 실행마다 결과가 달라질 수 있다.
 *   실제로 `universe.test.ts` 가 `get.mockReset is not a function` 으로 터졌다 —
 *   그 실행에서는 mock 이 아니라 **진짜 axios 인스턴스**가 들어왔다는 뜻이다.
 *   (같은 커밋을 다시 돌리면 통과했다. 코드가 아니라 실행 순서 문제라는 증거다.)
 *
 * 여기 한 곳에만 두면 모든 테스트가 **같은 등록**을 공유하고, 팩토리에서 메서드를
 * 빠뜨려 undefined 가 되는 사고도 원천적으로 없어진다.
 *
 * 📌 실제 모듈에 export 가 추가되면 여기도 같이 추가할 것 — 빠지면 그 값을 읽는
 *    컴포넌트가 테스트에서만 undefined 를 보게 된다.
 */
import { vi } from 'vitest';

/** axios 인스턴스 대역. 실제로 쓰지 않는 메서드까지 두는 건 파일별 누락을 막기 위해서다. */
export const api = {
  get: vi.fn(),
  post: vi.fn(),
  patch: vi.fn(),
  put: vi.fn(),
  delete: vi.fn(),
};

/** 구글 로그인 버튼이 읽는 값 — 실제 모듈과 형태를 맞춘다. */
export const backendOrigin = 'http://localhost:8080';

/** SSE(useWorkspaceEvents) 등이 401 응답 시 부르는 토큰 갱신. */
export const requestTokenRefresh = vi.fn();
