import { useInfiniteQuery } from '@tanstack/react-query';
import { fetchItems, workspaceProps } from '@/services/items';
import type { ItemListResponse } from '@/types/item';

/** 처리 중인 상태 폴링 간격 (ms) — PROCESSING 이 DONE 으로 바뀌는 걸 이 주기로 확인한다 */
const PROCESSING_POLL_MS = 3000;

/**
 * 폴링 간격을 정한다 — PROCESSING 이 하나라도 있으면 간격(ms), 없으면 false(정지).
 * refetchInterval 콜백에서 쓴다. 순수 함수라 타이머 없이 테스트한다.
 */
export function processingPollInterval(
  pages: ItemListResponse[] | undefined,
  ms = PROCESSING_POLL_MS,
): number | false {
  const hasProcessing = pages?.some((page) =>
    page.content.some((item) => item.status === 'PROCESSING'),
  );
  return hasProcessing ? ms : false;
}

/** 처리 중(PROCESSING)인 아이템 개수 — 성좌·지도 뷰의 "만드는 중" 배지가 몇 개인지 보여줄 때 쓴다. */
export function processingItemCount(pages: ItemListResponse[] | undefined): number {
  return (
    pages?.reduce(
      (sum, page) => sum + page.content.filter((item) => item.status === 'PROCESSING').length,
      0,
    ) ?? 0
  );
}

/** 특정 워크스페이스에 저장된 아이템 목록 — 서버 상태 진입점 */
export function useItems({ workspaceId, size, favorite, categoryIds, sort }: workspaceProps) {
  return useInfiniteQuery({
    // 필터를 queryKey 에 넣어야 값이 바뀔 때 0페이지부터 다시 받는다(캐시도 필터별로 분리)
    queryKey: ['items', workspaceId, size, favorite, categoryIds, sort],
    initialPageParam: 0, //0부터
    queryFn: ({ pageParam }) =>
      // ← react-query가 번호를 줌
      fetchItems({ workspaceId, page: pageParam, size, favorite, categoryIds, sort }),
    getNextPageParam: (lastPage) =>
      // ← page/setPage/hasMore 대체
      (lastPage.page + 1) * lastPage.size < lastPage.totalElements ? lastPage.page + 1 : undefined,
    // 처리 중인 아이템이 있을 때만 폴링한다. 다 끝나면 멈춰 불필요한 요청을 안 낸다.
    // (저장은 즉시 닫고 — NFR-001 — 분석 완료는 이 폴링이 조용히 채운다)
    refetchInterval: (query) => processingPollInterval(query.state.data?.pages),
  });
}
