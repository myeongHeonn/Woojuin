import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { fetchMyWorkspaces, updateWorkspace } from '@/services/workspaces';

/**
 * 내가 속한 워크스페이스 목록 — 사이드바 등에서 쓰는 서버 상태 진입점.
 *
 * refetchOnWindowFocus를 'always'로 강제하는 이유는 useWorkspaceMembers.useMembers와
 * 같다 — 탭이 숨겨진 사이(SSE 끊김) staleTime(30s) 안에 남이 나를 추방하거나 내가
 * 다른 워크스페이스에 가입해도, 복귀 시 focus 재요청이 건너뛰어 새로고침 전까진
 * 낡은 목록이 계속 보였다.
 */
export function useWorkspaces() {
  return useQuery({
    queryKey: ['workspaces'],
    queryFn: fetchMyWorkspaces,
    refetchOnWindowFocus: 'always',
  });
}

/** 이름 변경 — 헤더 제목·사이드바가 같은 목록 캐시를 읽으므로 무효화 한 번이면 전부 갱신된다 */
export function useRenameWorkspace(workspaceId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (name: string) => updateWorkspace(workspaceId, name),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['workspaces'] });
    },
  });
}
