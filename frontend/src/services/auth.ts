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
