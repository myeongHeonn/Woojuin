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
   * 가입할 때 서버가 만들어준 개인 워크스페이스 id.
   * 클라이언트가 알 방법이 없어서 프로필에 실려 온다.
   * TODO: 백엔드에 아직 없는 필드 — 응답에 추가되면 이 주석을 지운다
   */
  personalSpaceId: number;
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

export async function login(payload: LoginPayload) {
  const res = await api.post<ApiResponse<TokenResponse>>('/auth/login', payload);
  return res.data.data;
}

export async function logout() {
  await api.post<ApiResponse<null>>('/auth/logout');
}

export async function fetchMyProfile() {
  const res = await api.get<ApiResponse<UserProfile>>('/users/me');
  return res.data.data;
}
