import { useQuery } from '@tanstack/react-query';
import { fetchMyProfile } from '@/services/auth';

/**
 * 현재 로그인한 유저 정보 (닉네임·이메일·아바타색).
 * 서버 상태 진입점 — 소비처는 이 훅만 쓴다. 워크스페이스 목록은 useSpaces 참고.
 *
 * 로딩 전에는 **가짜 사용자를 만들어 내지 않는다.** 예전엔 목업(USER_MOCK)으로 채웠는데,
 * 그러면 남의 닉네임·이메일이 잠깐 보이는 것에서 끝나지 않는다 — userId 까지 가짜라
 * "이게 나인가"로 판정하는 곳(멤버 목록의 (나) 표시, 공유 모달의 내 역할)이 조용히
 * 틀린 답을 낸다. 값이 없으면 없는 대로 두고, 화면은 빈 문자열로 자리만 잡는다.
 *
 * userId 가 undefined 인 동안은 어떤 멤버와도 일치하지 않으므로 "나" 판정이 거짓양성을
 * 내지 않는다(프로필이 도착하면 다시 렌더된다).
 */
export function useUser() {
  const { data: profile, isLoading } = useQuery({
    queryKey: ['user', 'me'],
    queryFn: fetchMyProfile,
  });

  return {
    userId: profile?.id,
    nickName: profile?.nickname ?? '',
    email: profile?.email ?? '',
    avatarColor: profile?.avatarColor ?? '',
    profileImageUrl: profile?.profileImageUrl ?? null,
    provider: profile?.provider,
    emailVerified: profile?.emailVerified ?? false,
    isLoading,
  };
}
