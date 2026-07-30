import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { fetchMyWorkspaces, updateWorkspace } from '@/services/workspaces';

/** 내가 속한 워크스페이스 목록 — 사이드바 등에서 쓰는 서버 상태 진입점 */
export function useWorkspaces() {
  return useQuery({
    queryKey: ['workspaces'],
    queryFn: fetchMyWorkspaces,
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
