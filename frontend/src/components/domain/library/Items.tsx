import { useIntersectionObserver } from '@/hooks/useIntersectionObserver';
import { useItems } from '@/hooks/useItems';
import { useParams } from 'react-router-dom';
import { useCallback } from 'react';
import ItemCard from './ItemCard';
import EmptyState from './EmptyState';
import Spinner from '@/components/ui/Spinner';

interface ItemsProps {
  /** 즐겨찾기만 보기 — 서버 필터 */
  favorite?: boolean;
  /** 선택된 카테고리(OR) — 서버 필터. 빈 배열이면 전체 */
  categoryIds?: number[];
  /** 카드 클릭 → 상세 모달을 여는 건 부모(LibraryPage)가 정한다 */
  onOpenItem?: (itemId: number) => void;
}

const Items = ({ favorite, categoryIds, onOpenItem }: ItemsProps) => {
  const { workspaceId } = useParams<{ workspaceId: string }>();

  const { data, fetchNextPage, hasNextPage, isFetchingNextPage, isLoading } = useItems({
    workspaceId: Number(workspaceId),
    size: 20,
    favorite,
    categoryIds,
  });

  //응답들의 배열 → 아이템만 평평하게
  const items = data?.pages.flatMap((p) => p.content) ?? [];

  // "0개"의 원인 구분용 — 필터가 걸려 있으면 "결과 없음", 아니면 "첫 저장"
  const filtered = Boolean(favorite) || (categoryIds?.length ?? 0) > 0;

  //"바닥 보이면 뭐 할까" — 가드 + useCallback 으로 함수 고정
  //: useIntersectionObserver가 함수를 구독하고 있는데 이는 내부적으로 useEffect의 트리거 이다. 그러므로 useCallback으로 캐싱해서 불필요한 재호출을 막아야 한다.
  const onIntersect = useCallback(() => {
    if (hasNextPage && !isFetchingNextPage) fetchNextPage();
  }, [hasNextPage, isFetchingNextPage, fetchNextPage]);

  const ref = useIntersectionObserver(onIntersect);

  // 첫 로딩 — 아직 데이터가 없을 때 스피너 (빈 상태와 헷갈리지 않게)
  if (isLoading) {
    return (
      <div className="grid place-items-center py-24">
        <Spinner className="h-6 w-6" />
      </div>
    );
  }

  // 로딩이 끝났는데 0개 — 원인(필터 유무)에 맞는 빈 상태
  if (items.length === 0) {
    return <EmptyState filtered={filtered} />;
  }

  return (
    <div className="pt-8 pb-24">
      {/* 목업 그리드: 124px 카드가 auto-fill, 가운데 정렬 */}
      <div
        className="grid justify-center gap-x-[22px] gap-y-[30px]"
        style={{ gridTemplateColumns: 'repeat(auto-fill, 124px)' }}
      >
        {items.map((item) => (
          <ItemCard key={item.itemId} item={item} onClick={() => onOpenItem?.(item.itemId)} />
        ))}
      </div>

      {/* 바닥 감지용 요소 + 로딩/끝 표시 */}
      <div ref={ref} className="grid place-items-center py-6">
        {isFetchingNextPage && <Spinner className="h-6 w-6" />}
        {!hasNextPage && <span className="text-sm text-text-3">모두 불러왔어요</span>}
      </div>
    </div>
  );
};

export default Items;
