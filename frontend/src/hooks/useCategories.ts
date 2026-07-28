import { useQuery } from '@tanstack/react-query';
import { getCategories } from '@/services/workspaces';

/** 특정 워크스페이스의 카테고리 목록 — 대시보드 칩 바가 쓰는 서버 상태 진입점 */
export function useCategories(workspaceId: number) {
  return useQuery({
    // 카테고리는 워크스페이스마다 다르다 — id로 스코프해 목록('workspaces')과 캐시가 안 겹치게
    queryKey: ['categories', workspaceId],
    queryFn: () => getCategories(workspaceId),
  });
}
