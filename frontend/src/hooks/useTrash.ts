import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { fetchTrash, restoreItem, deleteItemPermanently } from '@/services/items';

const SIZE = 28;

/** 휴지통 목록 — 무한스크롤. 일반 목록과 같은 아이템 카드로 그린다. */
export function useTrash(workspaceId: number) {
  return useInfiniteQuery({
    queryKey: ['trash', workspaceId],
    initialPageParam: 0,
    queryFn: ({ pageParam }) => fetchTrash(workspaceId, pageParam, SIZE),
    getNextPageParam: (lastPage) =>
      (lastPage.page + 1) * lastPage.size < lastPage.totalElements ? lastPage.page + 1 : undefined,
  });
}

/** 복구 — 휴지통에서 빠지고 원래 목록으로 돌아간다(둘 다 갱신) */
export function useRestoreItem(workspaceId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (itemId: number) => restoreItem(itemId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['trash', workspaceId] });
      qc.invalidateQueries({ queryKey: ['items'] });
    },
  });
}

/** 영구 삭제 — 되돌릴 수 없다. 휴지통 목록만 갱신하면 된다. */
export function useDeletePermanently(workspaceId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (itemId: number) => deleteItemPermanently(itemId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['trash', workspaceId] }),
  });
}

/**
 * 전체 비우기 — 백엔드 일괄 엔드포인트가 없어 받은 id 들을 개별 영구삭제로 병렬 처리한다.
 * (불러온 항목 기준. 아주 많은 경우 페이지를 더 불러온 뒤 다시 비워야 한다)
 */
export function useEmptyTrash(workspaceId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (itemIds: number[]) => Promise.all(itemIds.map(deleteItemPermanently)),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['trash', workspaceId] }),
  });
}
