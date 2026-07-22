import { useQuery } from '@tanstack/react-query';
import { fetchMyProfile } from '@/services/auth';
import { fetchMyWorkspaces } from '@/services/workspaces';
import { USER_MOCK } from '@/stores/mock/user';
import { SPACEMAN_COLORS } from '@/utils/getSpacemanImage';

/**
 * 현재 로그인한 유저 정보 (닉네임·이메일·아바타색·저장공간·워크스페이스 목록).
 * 서버 상태 진입점 — 소비처는 이 훅만 쓴다.
 *
 * avatarColor(아바타 색상 선택)와 remainMemories/fullMemories(저장 공간 quota)는
 * 백엔드에 아직 없는 값이라 임시로 채운다. 프로필/워크스페이스 로딩 전에는
 * 화면이 비지 않도록 목업으로 대체한다.
 */
export function useUser() {
  const { data: profile } = useQuery({
    queryKey: ['user', 'me'],
    queryFn: fetchMyProfile,
  });
  const { data: workspaces } = useQuery({
    queryKey: ['workspaces'],
    queryFn: fetchMyWorkspaces,
  });

  if (!profile) {
    return USER_MOCK;
  }

  return {
    nickName: profile.nickname,
    email: profile.email,
    userId: profile.id,
    avatarColor: SPACEMAN_COLORS[profile.id % SPACEMAN_COLORS.length],
    remainMemories: USER_MOCK.remainMemories,
    fullMemories: USER_MOCK.fullMemories,
    workSpaces: workspaces?.map((ws) => ({ id: ws.id, name: ws.name })) ?? [],
  };
}
