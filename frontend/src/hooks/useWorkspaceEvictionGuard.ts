import { useMembers } from '@/hooks/useWorkspaceMembers';
import { useUser } from '@/hooks/useUser';

/**
 * 내가 이 워크스페이스에서 추방/제외됐는지 감지한다.
 *
 * 멤버 목록이 성공적으로 로드된 뒤에도 내 userId가 안 보이면 더 이상 멤버가 아니라는
 * 뜻이다. 목록은 SSE의 member 신호가 오면 다시 불러온다(useWorkspaceEvents 참고) —
 * 그래서 이미 화면에 있던 사람이 강퇴당해도 이 값이 뒤늦게라도 true로 바뀐다.
 *
 * 로딩 중이거나 조회 자체가 실패한 경우(애초에 멤버가 아니었던 접근 등)는 추방으로
 * 판단하지 않는다 — 그건 이 훅의 책임 밖이다.
 */
export function useWorkspaceEvictionGuard(workspaceId: number) {
  const { userId, isLoading: userLoading } = useUser();
  const { data: members, isLoading: membersLoading } = useMembers(workspaceId);

  const evicted =
    !userLoading &&
    !membersLoading &&
    userId !== undefined &&
    members !== undefined &&
    !members.some((member) => member.userId === userId);

  return { evicted };
}
