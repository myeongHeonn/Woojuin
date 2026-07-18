import axios from 'axios';

/** 공통 API 응답 형식 (API 명세서 기준) */
export interface ApiResponse<T> {
  status: number;
  message: string;
  data: T;
}

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api',
  timeout: 10_000,
});

// TODO(FR-001): JWT 인터셉터 — 토큰 첨부 / 401 시 리프레시
