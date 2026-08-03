import axios from 'axios';
import { useMembers } from '@/hooks/useWorkspaceMembers';

/**
 * 내가 이 워크스페이스에서 추방/제외됐는지 감지한다.
 *
 * 멤버 목록 API(GET /workspaces/:id/members)는 "현재 멤버"만 조회할 수 있다
 * (WorkspaceMemberService.list()가 findMembership으로 요청자 자신의 멤버십부터
 * 확인해서, 아닐 경우 403을 던진다) — 그래서 "성공 응답인데 내가 목록에 없다"는
 * 절대 일어나지 않고, 대신 조회 자체가 403으로 실패하는 형태로 나타난다.
 *
 * 목록은 SSE의 member 신호가 오면 다시 불러온다(useWorkspaceEvents 참고) — 그래서
 * 이미 화면에 있던 사람이 강퇴당해도 이 값이 뒤늦게라도 true로 바뀐다.
 *
 * 403이 아닌 다른 실패(네트워크 오류 등)는 추방으로 판단하지 않는다.
 */
export function useWorkspaceEvictionGuard(workspaceId: number) {
  const { error, isError } = useMembers(workspaceId);

  const evicted = isError && axios.isAxiosError(error) && error.response?.status === 403;

  return { evicted };
}
