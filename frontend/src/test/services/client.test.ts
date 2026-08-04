import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AxiosError, type AxiosResponse } from 'axios';
import { isRefreshRejected, refreshClient, requestTokenRefresh } from '@/services/client';
import { jotaiStore } from '@/stores/jotaiStore';
import { accessTokenAtom, refreshTokenAtom } from '@/stores/authAtoms';

/**
 * refresh 실패를 어떻게 다루는지가 이 파일의 검증 대상이다.
 *
 * 모바일에서 앱을 나갔다 들어오면 로그인이 풀리던 원인이 여기였다 — 네트워크 실패까지
 * "토큰이 무효하다"로 취급해 지워 버렸다. 복귀 시점엔 access 가 이미 만료돼 있고 쿼리가
 * 한꺼번에 나가며 통신이 불안정해서, 한 번만 실패해도 되돌릴 수 없는 로그아웃이 됐다.
 *
 * **모듈 mock(`vi.mock`)을 쓰지 않는다** — browser mode 에서 적용되지 않는 실행이 있어
 * 간헐 실패의 원인이 된다(test/helpers/stubApi.ts 주석에 그 조사 결과가 있다). 대신
 * refreshClient 인스턴스의 post 를 직접 갈아끼운다. mock 적용 여부와 무관하게 항상
 * 동작하므로 간헐 실패의 조건 자체가 없다.
 */
const post = vi.fn();
const originalPost = refreshClient.post;

/** 서버가 상태 코드로 답한 실패 */
const responded = (status: number) =>
  new AxiosError('failed', 'ERR_BAD_RESPONSE', undefined, undefined, {
    status,
    data: {},
    statusText: '',
    headers: {},
    config: {},
  } as AxiosResponse);

/** 응답을 받지 못한 실패 — 통신 끊김·타임아웃 */
const noResponse = (code: string) => new AxiosError('failed', code);

beforeEach(() => {
  post.mockReset();
  (refreshClient as unknown as Record<string, unknown>).post = post;
  jotaiStore.set(accessTokenAtom, 'old-access');
  jotaiStore.set(refreshTokenAtom, 'stored-refresh');
});

afterEach(() => {
  // 모듈 인스턴스가 파일 간에 공유될 수 있으므로 반드시 되돌린다
  (refreshClient as unknown as Record<string, unknown>).post = originalPost;
  localStorage.clear();
});

describe('isRefreshRejected — 서버가 토큰을 거부한 것인지 가른다', () => {
  it('무효·만료된 refresh token 은 이 백엔드에서 400 으로 온다', () => {
    // TokenRefreshService 가 IllegalArgumentException 을 던지고 GlobalExceptionHandler 가
    // 400 으로 매핑한다. 401·403 만 보면 진짜 만료된 사용자가 로그인 화면으로 못 간다
    expect(isRefreshRejected(responded(400))).toBe(true);
  });

  it('401·403 도 거부로 센다', () => {
    expect(isRefreshRejected(responded(401))).toBe(true);
    expect(isRefreshRejected(responded(403))).toBe(true);
  });

  it('응답을 받지 못한 실패는 거부가 아니다', () => {
    // 서버 판단을 받은 적이 없다 — 토큰이 무효하다고 단정할 근거가 없다
    expect(isRefreshRejected(noResponse('ERR_NETWORK'))).toBe(false);
    expect(isRefreshRejected(noResponse('ECONNABORTED'))).toBe(false);
  });

  it('서버 장애(5xx)와 과호출(429)도 거부가 아니다', () => {
    for (const status of [500, 502, 503, 504, 429]) {
      expect(isRefreshRejected(responded(status))).toBe(false);
    }
  });

  it('axios 에러가 아닌 예외는 거부가 아니다', () => {
    expect(isRefreshRejected(new Error('무언가 터졌다'))).toBe(false);
    expect(isRefreshRejected(undefined)).toBe(false);
  });
});

describe('requestTokenRefresh — 실패 종류에 따라 토큰을 남기거나 지운다', () => {
  it('성공하면 새 토큰으로 갈아 끼운다', async () => {
    post.mockResolvedValue({
      data: { data: { accessToken: 'new-access', refreshToken: 'new-refresh' } },
    });

    await expect(requestTokenRefresh()).resolves.toBe('new-access');
    expect(jotaiStore.get(accessTokenAtom)).toBe('new-access');
    expect(jotaiStore.get(refreshTokenAtom)).toBe('new-refresh');
  });

  it('통신이 끊기면 토큰을 지우지 않는다', async () => {
    post.mockRejectedValue(noResponse('ERR_NETWORK'));

    await expect(requestTokenRefresh()).resolves.toBeNull();
    // 남아 있어야 통신이 돌아왔을 때 다시 시도할 수 있다 (= 로그아웃되지 않는다)
    expect(jotaiStore.get(accessTokenAtom)).toBe('old-access');
    expect(jotaiStore.get(refreshTokenAtom)).toBe('stored-refresh');
  });

  it('서버 장애(500)에도 토큰을 지우지 않는다', async () => {
    post.mockRejectedValue(responded(500));

    await expect(requestTokenRefresh()).resolves.toBeNull();
    expect(jotaiStore.get(refreshTokenAtom)).toBe('stored-refresh');
  });

  it('서버가 거부하면(400) 토큰을 지워 로그인 화면으로 보낸다', async () => {
    post.mockRejectedValue(responded(400));

    await expect(requestTokenRefresh()).resolves.toBeNull();
    // 여기서 안 지우면 만료된 사용자가 모든 요청이 실패하는 상태에 갇힌다
    expect(jotaiStore.get(accessTokenAtom)).toBeNull();
    expect(jotaiStore.get(refreshTokenAtom)).toBeNull();
  });

  it('refresh token 이 없으면 요청조차 하지 않는다', async () => {
    jotaiStore.set(refreshTokenAtom, null);

    await expect(requestTokenRefresh()).resolves.toBeNull();
    expect(post).not.toHaveBeenCalled();
  });

  it('동시에 여러 번 불러도 실제 요청은 한 번만 나간다', async () => {
    // 복귀 직후엔 쿼리 여러 개가 한꺼번에 401 을 맞는다 — 그때 refresh 가 겹쳐 나가면 안 된다
    post.mockResolvedValue({
      data: { data: { accessToken: 'new-access', refreshToken: 'new-refresh' } },
    });

    const results = await Promise.all([
      requestTokenRefresh(),
      requestTokenRefresh(),
      requestTokenRefresh(),
    ]);

    expect(results).toEqual(['new-access', 'new-access', 'new-access']);
    expect(post).toHaveBeenCalledTimes(1);
  });
});
