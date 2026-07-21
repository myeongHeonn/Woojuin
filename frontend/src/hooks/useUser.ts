import { USER_MOCK } from '@/stores/mock/user';

/**
 * 현재 로그인한 유저 정보 (닉네임·이메일·아바타색·저장공간·워크스페이스 목록).
 *
 * 서버 상태 진입점 — 소비처는 이 훅만 쓰고, 실제 연동 시 여기만 바꾼다.
 * TODO: useQuery({ queryKey: ['user'], queryFn: fetchUser }) 로 교체
 */
export const useUser = () => USER_MOCK;
