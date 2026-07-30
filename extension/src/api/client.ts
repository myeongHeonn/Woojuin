import { clearTokens, getAccessToken, getRefreshToken, saveTokens } from '@/storage/authStorage';

const DEFAULT_API_BASE_URLS: Record<string, string> = {
  development: 'http://localhost:8080/api',
  demo: 'https://dev.api.woojuin.store/api',
  production: 'https://api.woojuin.store/api',
};

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL
  ?? DEFAULT_API_BASE_URLS[import.meta.env.MODE]
  ?? DEFAULT_API_BASE_URLS.production;

interface ApiResponse<T> { status: number; message: string; data: T }
interface TokenData { accessToken: string; refreshToken: string }

export class ApiError extends Error {
  constructor(message: string, public readonly status: number) {
    super(message);
  }
}

let refreshPromise: Promise<string> | null = null;

async function parseResponse<T>(response: Response): Promise<ApiResponse<T>> {
  let body: ApiResponse<T> | null = null;
  try { body = (await response.json()) as ApiResponse<T>; } catch { /* non-JSON error */ }
  if (!response.ok || !body) {
    throw new ApiError(body?.message || `요청에 실패했습니다. (${response.status})`, response.status);
  }
  return body;
}

async function refreshAccessToken(): Promise<string> {
  if (refreshPromise) return refreshPromise;
  refreshPromise = (async () => {
    const refreshToken = await getRefreshToken();
    if (!refreshToken) throw new ApiError('로그인이 필요합니다.', 401);
    try {
      const response = await fetch(`${API_BASE_URL}/auth/token/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      });
      const { data } = await parseResponse<TokenData>(response);
      await saveTokens(data.accessToken, data.refreshToken);
      return data.accessToken;
    } catch (error) {
      await clearTokens();
      throw error;
    }
  })().finally(() => { refreshPromise = null; });
  return refreshPromise;
}

export async function apiFetch<T>(path: string, init: RequestInit = {}, retry = true): Promise<T> {
  const headers = new Headers(init.headers);
  const accessToken = await getAccessToken();
  if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`);
  const response = await fetch(`${API_BASE_URL}${path}`, { ...init, headers });
  if (response.status === 401 && retry) {
    const token = await refreshAccessToken();
    headers.set('Authorization', `Bearer ${token}`);
    return apiFetch<T>(path, { ...init, headers }, false);
  }
  return (await parseResponse<T>(response)).data;
}

export async function publicApiFetch<T>(path: string, init: RequestInit): Promise<T> {
  return (await parseResponse<T>(await fetch(`${API_BASE_URL}${path}`, init))).data;
}
