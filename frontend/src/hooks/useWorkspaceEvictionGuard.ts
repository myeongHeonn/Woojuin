import axios from 'axios';
import { useMembers } from '@/hooks/useWorkspaceMembers';
import type { ApiResponse } from '@/services/client';

/**
 * 내가 이 워크스페이스에서 추방됐는지, 아니면 애초에 접근 권한이 없는지 감지한다.
 *
 * 멤버 목록 API(GET /workspaces/:id/members)는 "현재 멤버"만 조회할 수 있다
 * (WorkspaceMemberService.list()가 요청자 자신의 멤버십부터 확인해서, 아닐 경우
 * 403을 던진다) — 그래서 "성공 응답인데 내가 목록에 없다"는 절대 일어나지 않고,
 * 대신 조회 자체가 403으로 실패하는 형태로 나타난다.
 *
 * 같은 403이라도 두 상황을 구분해야 한다: (1) 강퇴당함 (2) 자진 탈퇴했거나 애초에
 * 멤버였던 적이 없어 접근 권한이 없음. 백엔드가 강퇴 이력(workspace_bans)이 있을
 * 때만 메시지에 "추방"이 들어간 WorkspaceBannedException을 던지므로 그 문자열로
 * 구분한다(InvitePage의 재입장 차단 안내와 같은 패턴) — react-query 캐시 상태에
 * 기대지 않으므로, 강퇴당한 사실을 모른 채 나중에(새로고침 후·다른 화면에 있다가)
 * 그 워크스페이스로 처음 들어와도 정확히 "추방"으로 판정된다.
 *
 * 목록은 SSE의 member 신호가 오면 다시 불러온다(useWorkspaceEvents 참고) — 그래서
 * 이미 화면에 있던 사람이 강퇴당해도 이 값이 실시간으로 true가 된다.
 *
 * 403이 아닌 다른 실패(네트워크 오류 등)는 둘 다로 판단하지 않는다.
 */
export function useWorkspaceEvictionGuard(workspaceId: number) {
  const { error, isError } = useMembers(workspaceId);

  const is403 = isError && axios.isAxiosError(error) && error.response?.status === 403;
  const message = axios.isAxiosError(error)
    ? (error.response?.data as ApiResponse<unknown> | undefined)?.message
    : undefined;
  const isBanned = message?.includes('추방') ?? false;

  return {
    evicted: is403 && isBanned,
    accessDenied: is403 && !isBanned,
  };
}
