import axios from 'axios';
import { jotaiStore } from '@/stores/jotaiStore';
import { accessTokenAtom, refreshTokenAtom } from '@/stores/authAtoms';

/** 공통 API 응답 형식 (API 명세서 기준) */
export interface ApiResponse<T> {
  status: number;
  message: string;
  data: T;
}

interface TokenPair {
  accessToken: string;
  refreshToken: string;
}

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api',
  timeout: 10_000,
});

// 구글 로그인(oauth2/authorization/...)은 /api 아래가 아니라 백엔드 루트 경로라서
// 별도로 origin만 떼어 써야 한다. VITE_API_BASE_URL이 비어있으면(설정 누락 등)
// baseURL이 '/api'(상대경로)로 떨어지는데, 그걸 그대로 잘라내면 빈 문자열이 되어
// 버튼이 프론트 자기 자신으로 이동해버리니 절대 URL 기본값을 둔다.
export const backendOrigin =
  import.meta.env.VITE_API_BASE_URL?.replace(/\/api\/?$/, '') || 'http://localhost:8080';

/**
 * refresh 호출 전용. api 인스턴스로 호출하면 인터셉터가 다시 붙어
 * 만료된 access token을 헤더에 실은 채 요청하게 되니 별도 인스턴스를 쓴다.
 *
 * export 하는 이유는 테스트다 — refresh 실패 처리를 검증하려면 이 인스턴스의 응답을
 * 통제해야 하는데, `vi.mock` 은 browser mode 에서 **적용되지 않는 실행**이 있어 간헐
 * 실패의 원인이 된다(test/helpers/stubApi.ts 주석 참고). 인스턴스 메서드는 일반 속성이라
 * 테스트가 직접 갈아끼울 수 있으므로, 모듈 mock 없이 통제하려면 이게 밖에서 보여야 한다.
 * 앱 코드에서는 쓰지 않는다.
 */
export const refreshClient = axios.create({
  baseURL: api.defaults.baseURL,
  timeout: 10_000,
});

api.interceptors.request.use((config) => {
  const accessToken = jotaiStore.get(accessTokenAtom);
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`;
  }
  return config;
});

/**
 * 서버가 refresh token 을 **거부한** 응답 코드.
 *
 * 이 백엔드는 무효·만료·불일치·탈퇴를 전부 `IllegalArgumentException` 으로 던지고
 * GlobalExceptionHandler 가 그걸 **400** 으로 매핑한다(TokenRefreshService 참고).
 * 그래서 401·403 만 보면 진짜로 만료된 사용자가 로그인 화면으로 못 가고, 모든 요청이
 * 실패하는 상태에 갇힌다. 401·403 은 인증 필터가 앞단에서 막는 경우를 위해 함께 둔다.
 */
const REFRESH_REJECTED_STATUSES = [400, 401, 403];

/**
 * refresh 실패를 "서버가 토큰을 거부했다" 와 "지금은 물어보지도 못했다" 로 가른다.
 *
 * 이 구분이 없으면 — 네트워크 실패·타임아웃·5xx 까지 거부로 취급하면 — 모바일에서 앱을
 * 다시 열 때마다 로그아웃될 수 있다. 복귀 시점에는 (a) access 가 이미 만료돼 있고
 * (b) 쿼리 여러 개가 한꺼번에 나가고 (c) 셀룰러 복귀·와이파이 전환으로 통신이 불안정하다.
 * 한 번만 실패해도 토큰이 지워지면 되돌릴 방법이 없다.
 *
 * 응답이 아예 없으면(`error.response` 가 undefined) 서버 판단을 받은 적이 없다는 뜻이므로
 * 거부가 아니다 — 토큰을 남겨 두고 통신이 돌아왔을 때 다시 시도한다.
 */
export function isRefreshRejected(error: unknown): boolean {
  if (!axios.isAxiosError(error)) return false;
  const status = error.response?.status;
  if (status === undefined) return false;
  return REFRESH_REJECTED_STATUSES.includes(status);
}

let refreshPromise: Promise<string | null> | null = null;

/**
 * 토큰 갱신을 요청한다. 동시에 여러 곳이 401을 맞아도 실제 refresh는 한 번만 나간다.
 * axios 인터셉터 밖(SSE 연결 등)에서도 같은 정책을 타야 해서 함수로 노출한다.
 */
export function requestTokenRefresh(): Promise<string | null> {
  refreshPromise ??= refreshAccessToken().finally(() => {
    refreshPromise = null;
  });
  return refreshPromise;
}

async function refreshAccessToken(): Promise<string | null> {
  const refreshToken = jotaiStore.get(refreshTokenAtom);
  if (!refreshToken) return null;

  try {
    const { data } = await refreshClient.post<ApiResponse<TokenPair>>('/auth/token/refresh', {
      refreshToken,
    });
    jotaiStore.set(accessTokenAtom, data.data.accessToken);
    jotaiStore.set(refreshTokenAtom, data.data.refreshToken);
    return data.data.accessToken;
  } catch (error) {
    // 서버가 거부한 게 아니면 토큰을 남긴다 — 통신이 돌아오면 다시 시도할 수 있다.
    // (AuthLayout 은 accessToken 이 있으면 화면을 유지하므로, 만료된 토큰이라도 남겨 두면
    //  로그인 화면으로 튕기지 않고 그 자리에서 재시도가 이어진다)
    if (isRefreshRejected(error)) {
      jotaiStore.set(accessTokenAtom, null);
      jotaiStore.set(refreshTokenAtom, null);
    }
    return null;
  }
}

/**
 * 자격 증명을 제출하는 요청들 — 이들의 401은 "access token이 만료됐다"가 아니라
 * "이메일·비밀번호가 틀렸다"는 뜻이라 토큰 갱신 대상이 아니다.
 *
 * 걸러내지 않으면 로그아웃 뒤 localStorage에 남아 있던 refresh token으로 갱신이 성공해
 * **로그인에 실패한 화면 뒤에서 이전 사용자로 로그인되는** 상태가 만들어진다.
 */
const CREDENTIAL_ENDPOINTS = ['/auth/login', '/auth/signup'];

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const original = error.config;
    const isCredentialRequest = CREDENTIAL_ENDPOINTS.includes(original?.url ?? '');
    if (error.response?.status === 401 && !original._retry && !isCredentialRequest) {
      original._retry = true;
      const newAccessToken = await requestTokenRefresh();
      if (newAccessToken) {
        original.headers.Authorization = `Bearer ${newAccessToken}`;
        return api(original);
      }
    }
    return Promise.reject(error);
  },
);
