import { atomWithStorage, createJSONStorage } from 'jotai/utils';

// localStorage를 직접 쓰지 않고 Jotai atomWithStorage로 감싼다 (AGENTS.md 컨벤션).
// getOnInit: true로 안 두면 첫 렌더에서 무조건 initialValue(null)부터 시작해서
// (마운트 이펙트가 돌기 전까지) 로그인돼 있어도 "토큰 없음"으로 보인다 —
// AuthLayout의 가드가 이 첫 렌더 타이밍에 반응해 즉시 리다이렉트시켜버렸었다.
export const accessTokenAtom = atomWithStorage<string | null>(
  'woojuin:accessToken',
  null,
  undefined,
  {
    getOnInit: true,
  },
);
export const refreshTokenAtom = atomWithStorage<string | null>(
  'woojuin:refreshToken',
  null,
  undefined,
  {
    getOnInit: true,
  },
);

// OAuth 로그인은 백엔드·구글을 거치는 전체 페이지 이동이라 원래 가려던 경로를
// 컴포넌트 state로는 못 들고 다닌다. 로그인 토큰과 달리 이 세션에서만 유효하면
// 되므로 localStorage가 아닌 sessionStorage에 둔다.
// getOnInit: true 필요 이유는 위 accessTokenAtom과 동일 — OAuthCallbackPage는 전체 페이지
// 리로드로 새로 마운트되므로, 이 값 없이는 첫 렌더가 initialValue(null)로 시작해 마운트
// 이펙트에서 곧장 postLoginRedirect를 null로 읽고 기본 경로(/home)로 보내버린다.
export const postLoginRedirectAtom = atomWithStorage<string | null>(
  'woojuin:postLoginRedirect',
  null,
  createJSONStorage(() => sessionStorage),
  {
    getOnInit: true,
  },
);
