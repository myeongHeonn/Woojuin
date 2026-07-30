import { useInfiniteQuery } from '@tanstack/react-query';
import { searchItems, aiSearchItems } from '@/services/items';
import type { ItemAiSearchResponse } from '@/types/item';

const SIZE = 28;

/** 검색 캐시 키 — 취소(cancelQueries)에도 이 형태를 쓴다 */
export const searchKey = (workspaceId: number, aiMode: boolean, q: string) =>
  ['search', workspaceId, aiMode, q] as const;

/**
 * 아이템 검색 — 성좌·대시보드가 공유하는 로직(기능은 같고 UI만 다르다).
 * aiMode 로 엔드포인트를 가르고(일반 /search · AI /ai/search), q 가 비면 아예 요청하지 않는다.
 * 응답 content 는 목록과 같아 ItemCard 를 그대로 재사용한다.
 */
export function useSearch(workspaceId: number, q: string, aiMode: boolean) {
  const query = q.trim();

  const result = useInfiniteQuery({
    queryKey: searchKey(workspaceId, aiMode, query),
    enabled: query.length > 0,
    initialPageParam: 0,
    queryFn: ({ pageParam }) =>
      aiMode
        ? aiSearchItems(workspaceId, query, pageParam, SIZE)
        : searchItems(workspaceId, query, pageParam, SIZE),
    getNextPageParam: (lastPage) =>
      (lastPage.page + 1) * lastPage.size < lastPage.totalElements ? lastPage.page + 1 : undefined,
  });

  const items = result.data?.pages.flatMap((page) => page.content) ?? [];
  const last = result.data?.pages.at(-1);

  return {
    ...result,
    items,
    partialMatch: last?.partialMatch ?? false,
    // AI 전용 안내 — 일반 검색이면 undefined
    interpretedQuery: aiMode
      ? (last as ItemAiSearchResponse | undefined)?.interpretedQuery
      : undefined,
    aiPlanned: aiMode ? (last as ItemAiSearchResponse | undefined)?.aiPlanned : undefined,
  };
}
