import { useQuery } from '@tanstack/react-query';
import { fetchItem } from '@/services/items';

/**
 * 아이템 상세 — 모달이 열렸을 때만(itemId 가 있을 때만) 받는다.
 * 목록(useItems)엔 없는 본문(content)·원본 이미지가 여기서 온다.
 * itemId=null 이면 비활성(enabled:false) — 닫혀 있을 땐 요청하지 않는다.
 */
export function useItem(workspaceId: number, itemId: number | null) {
  return useQuery({
    queryKey: ['item', workspaceId, itemId],
    queryFn: () => fetchItem(itemId as number),
    enabled: itemId != null,
  });
}
