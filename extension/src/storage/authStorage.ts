const ACCESS_TOKEN_KEY = 'accessToken';
const REFRESH_TOKEN_KEY = 'refreshToken';

/**
 * 저장 위치가 갈린다 — 팝업이 storage.onChanged 로 로그인 도착을 감지할 때 영역별로 키를
 * 봐야 하므로 밖에 알린다. (액세스 토큰은 브라우저를 닫으면 사라지는 session,
 * 리프레시 토큰은 다시 열어도 이어지도록 local)
 */
export const AUTH_STORAGE = {
  access: { area: 'session', key: ACCESS_TOKEN_KEY },
  refresh: { area: 'local', key: REFRESH_TOKEN_KEY },
} as const;

export async function getAccessToken(): Promise<string | null> {
  const result = await chrome.storage.session.get(ACCESS_TOKEN_KEY);
  return typeof result[ACCESS_TOKEN_KEY] === 'string' ? result[ACCESS_TOKEN_KEY] : null;
}

export async function getRefreshToken(): Promise<string | null> {
  const result = await chrome.storage.local.get(REFRESH_TOKEN_KEY);
  return typeof result[REFRESH_TOKEN_KEY] === 'string' ? result[REFRESH_TOKEN_KEY] : null;
}

export async function saveTokens(accessToken: string, refreshToken: string): Promise<void> {
  await Promise.all([
    chrome.storage.session.set({ [ACCESS_TOKEN_KEY]: accessToken }),
    chrome.storage.local.set({ [REFRESH_TOKEN_KEY]: refreshToken }),
  ]);
}

/**
 * 자동 로그인 차단 플래그.
 *
 * 확장은 우주인 탭에 살아 있는 세션을 자동으로 물려받는다(webSession.ts). 편하지만 그대로면
 * **로그아웃이 무의미해진다** — 확장 토큰만 지워도 팝업을 다시 열 때 곧바로 재수확되기 때문이다.
 * 그래서 로그아웃은 이 플래그를 세우고, 사용자가 로그인 버튼을 누를 때만 내린다.
 * 브라우저를 닫아도 유지되도록 local 에 둔다(session 이면 재시작으로 풀려 버린다).
 */
const AUTO_LOGIN_SUPPRESSED_KEY = 'autoLoginSuppressed';

export async function isAutoLoginSuppressed(): Promise<boolean> {
  const result = await chrome.storage.local.get(AUTO_LOGIN_SUPPRESSED_KEY);
  return result[AUTO_LOGIN_SUPPRESSED_KEY] === true;
}

export async function setAutoLoginSuppressed(suppressed: boolean): Promise<void> {
  if (suppressed) {
    await chrome.storage.local.set({ [AUTO_LOGIN_SUPPRESSED_KEY]: true });
    return;
  }
  await chrome.storage.local.remove(AUTO_LOGIN_SUPPRESSED_KEY);
}

export async function clearTokens(): Promise<void> {
  await Promise.all([
    chrome.storage.session.remove(ACCESS_TOKEN_KEY),
    chrome.storage.local.remove(REFRESH_TOKEN_KEY),
  ]);
}
