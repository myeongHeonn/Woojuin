import { api, type ApiResponse } from './client';

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
}

export interface UserProfile {
  id: number;
  email: string;
  nickname: string;
  profileImageUrl: string | null;
  provider: 'LOCAL' | 'KAKAO' | 'GOOGLE';
  emailVerified: boolean;
  /**
   * 아바타 색 — 서버는 대문자로 준다("WHITE").
   * 이미지 매핑은 getSpacemanImage 가 대소문자를 맞춰 처리하므로 그대로 넘긴다.
   * 좁은 유니온 대신 string 인 이유: 서버가 새 색을 추가해도 화면이 깨지지 않아야 한다.
   */
  avatarColor: string;
  /**
   * 가입할 때 서버가 만들어준 개인 워크스페이스 id.
   * 클라이언트가 알 방법이 없어서 프로필에 실려 온다.
   */
  personalSpaceId: number;
  personalTutorialCompleted: boolean;
  sharedWorkspaceTutorialCompleted: boolean;
}

export interface UpdateProfilePayload {
  nickname: string;
  profileImageUrl: string | null;
  avatarColor: string;
}

export interface UserStats {
  totalSaved: number;
  workspaceCount: number;
  savedThisWeek: number;
}

export interface AiUsage {
  period: string;
  used: number;
  limit: number;
  remaining: number | null;
  unlimited: boolean;
  limitEnabled: boolean;
  resetAt: string;
}

export interface SignupPayload {
  email: string;
  password: string;
  nickname: string;
}

export interface LoginPayload {
  email: string;
  password: string;
}

export async function signup(payload: SignupPayload) {
  const res = await api.post<ApiResponse<UserProfile>>('/auth/signup', payload);
  return res.data.data;
}

export async function checkEmailAvailability(email: string) {
  const res = await api.get<ApiResponse<{ available: boolean }>>('/auth/check-email', {
    params: { email },
  });
  return res.data.data.available;
}

export async function login(payload: LoginPayload) {
  const res = await api.post<ApiResponse<TokenResponse>>('/auth/login', payload);
  return res.data.data;
}

export async function logout() {
  await api.post<ApiResponse<null>>('/auth/logout');
}

export async function withdraw() {
  await api.delete<ApiResponse<null>>('/users/me');
}

export async function fetchMyProfile() {
  const res = await api.get<ApiResponse<UserProfile>>('/users/me');
  return res.data.data;
}

export async function fetchMyStats() {
  const res = await api.get<ApiResponse<UserStats>>('/users/me/stats');
  return res.data.data;
}

export async function fetchMyAiUsage() {
  const res = await api.get<ApiResponse<AiUsage>>('/users/me/ai-usage');
  return res.data.data;
}

export async function updateMyProfile(payload: UpdateProfilePayload) {
  const res = await api.patch<ApiResponse<UserProfile>>('/users/me', payload);
  return res.data.data;
}

/**
 * 로그인된 기기(세션) 하나 — 마이페이지 "연결된 기기" 행 (S15P11C105-459/-460).
 * deviceName 은 서버가 로그인 User-Agent 를 해석해 만든 것("Windows · Chrome" 수준).
 * current 는 서버가 판별한다 — 클라이언트는 자기 sid 를 모른다(토큰 안 클레임).
 */
export interface DeviceSession {
  sessionId: string;
  deviceName: string;
  createdAt: string;
  lastUsedAt: string;
  current: boolean;
}

export async function fetchSessions() {
  const res = await api.get<ApiResponse<DeviceSession[]>>('/auth/sessions');
  return res.data.data;
}

/** 특정 기기 해제 — 그 기기는 다음 요청부터 즉시 막힌다. 현재 기기면 로그아웃과 같다. */
export async function revokeSession(sessionId: string) {
  await api.delete<ApiResponse<null>>(`/auth/sessions/${encodeURIComponent(sessionId)}`);
}

/** 모든 기기에서 로그아웃 — 호출한 기기도 끊기므로 응답 후 로그인 화면으로 가야 한다. */
export async function revokeAllSessions() {
  await api.delete<ApiResponse<null>>('/auth/sessions');
}

/**
 * 워치 화면의 링크 코드를 승인한다 (S15P11C105-458).
 * 승인되면 워치가 폴링으로 토큰을 받아 가고, 몇 초 안에 기기 목록에 워치가 나타난다.
 * 만료·오타 코드는 400 — 워치에서 새 코드를 확인해야 한다.
 */
export async function approveDeviceLink(code: string) {
  await api.post<ApiResponse<null>>('/auth/device-link/approve', { code });
}

export type TutorialType = 'PERSONAL' | 'SHARED_WORKSPACE';

export async function completeTutorial(tutorialType: TutorialType) {
  const res = await api.patch<ApiResponse<UserProfile>>(
    `/users/me/tutorials/${tutorialType}/complete`,
  );
  return res.data.data;
}
