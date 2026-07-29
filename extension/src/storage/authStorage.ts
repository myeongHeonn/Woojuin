const ACCESS_TOKEN_KEY = 'accessToken';
const REFRESH_TOKEN_KEY = 'refreshToken';

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

export async function clearTokens(): Promise<void> {
  await Promise.all([
    chrome.storage.session.remove(ACCESS_TOKEN_KEY),
    chrome.storage.local.remove(REFRESH_TOKEN_KEY),
  ]);
}
