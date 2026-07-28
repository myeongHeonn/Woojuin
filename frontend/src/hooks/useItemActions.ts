import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { ItemDetail } from '@/types/item';
import {
  updateItem,
  addFavorite,
  removeFavorite,
  deleteItem,
  type ItemPatch,
} from '@/services/items';

/** 성공 후 상세(item)·목록(items) 캐시를 무효화해 화면을 최신으로 맞춘다 */
function useInvalidateItems() {
  const qc = useQueryClient();
  return () => {
    qc.invalidateQueries({ queryKey: ['item'] });
    qc.invalidateQueries({ queryKey: ['items'] });
  };
}

/**
 * 아이템 부분 수정 — 제목/본문/카테고리 편집. PATCH /items/{id}.
 * UI 는 무엇을 바꾸는지만 넘기고(예: { categoryIds }), 통신·캐시 갱신은 여기서 한다(동작/표현 분리).
 */
export function useUpdateItem(itemId: number) {
  const invalidate = useInvalidateItems();
  return useMutation({
    mutationFn: (patch: ItemPatch) => updateItem(itemId, patch),
    onSuccess: invalidate,
  });
}

/**
 * 즐겨찾기 등록/해제 — 켜면 POST, 끄면 DELETE (전용 엔드포인트).
 * mutate(true)=등록, mutate(false)=해제.
 * 응답의 { itemId, favorite } 로 상세 캐시의 favorite 만 바로 갈아끼우고(재요청 없이 즉시 반영),
 * 목록은 즐겨찾기 필터 결과가 달라질 수 있어 무효화한다.
 */
export function useSetFavorite(itemId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (favorite: boolean) => (favorite ? addFavorite(itemId) : removeFavorite(itemId)),
    onSuccess: (res) => {
      qc.setQueriesData<ItemDetail>({ queryKey: ['item'] }, (prev) =>
        prev && prev.itemId === res.itemId ? { ...prev, favorite: res.favorite } : prev,
      );
      qc.invalidateQueries({ queryKey: ['items'] });
    },
  });
}

/** 아이템 삭제(휴지통 이동). 성공하면 목록을 갱신한다 — 모달 닫기는 호출부가 정한다. */
export function useDeleteItem() {
  const invalidate = useInvalidateItems();
  return useMutation({
    mutationFn: (itemId: number) => deleteItem(itemId),
    onSuccess: invalidate,
  });
}
