import { useInfiniteQuery } from '@tanstack/react-query';
import { fetchItems, workspaceProps } from '@/services/items';

/** 특정 워크스페이스에 저장된 아이템 목록 — 서버 상태 진입점 */
export function useItems({ workspaceId, size }: workspaceProps) {
  return useInfiniteQuery({
    queryKey: ['items', workspaceId, size],
    initialPageParam: 0, //0부터
    queryFn: ({ pageParam }) =>
      // ← react-query가 번호를 줌
      fetchItems({ workspaceId, page: pageParam, size }),
    getNextPageParam: (lastPage) =>
      // ← page/setPage/hasMore 대체
      (lastPage.page + 1) * lastPage.size < lastPage.totalElements ? lastPage.page + 1 : undefined,
  });
}
