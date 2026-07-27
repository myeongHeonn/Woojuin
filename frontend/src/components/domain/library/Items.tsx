import { useIntersectionObserver } from '@/hooks/useIntersectionObserver';
import { useItems } from '@/hooks/useItems';
import { useParams } from 'react-router-dom';
import { useCallback } from 'react';
import ItemCard from './ItemCard';
import Spinner from '@/components/ui/Spinner';

const Items = () => {
  const { workspaceId } = useParams<{ workspaceId: string }>();

  const { data, fetchNextPage, hasNextPage, isFetchingNextPage } = useItems({
    workspaceId: Number(workspaceId),
    size: 20,
  });

  //응답들의 배열 → 아이템만 평평하게
  const items = data?.pages.flatMap((p) => p.content) ?? [];

  //"바닥 보이면 뭐 할까" — 가드 + useCallback 으로 함수 고정
  //: useIntersectionObserver가 함수를 구독하고 있는데 이는 내부적으로 useEffect의 트리거 이다. 그러므로 useCallback으로 캐싱해서 불필요한 재호출을 막아야 한다.
  const onIntersect = useCallback(() => {
    if (hasNextPage && !isFetchingNextPage) fetchNextPage();
  }, [hasNextPage, isFetchingNextPage, fetchNextPage]);

  const ref = useIntersectionObserver(onIntersect);

  return (
    <div className="pt-8 pb-24">
      {/* 목업 그리드: 124px 카드가 auto-fill, 가운데 정렬 */}
      <div
        className="grid justify-center gap-x-[22px] gap-y-[30px]"
        style={{ gridTemplateColumns: 'repeat(auto-fill, 124px)' }}
      >
        {items.map((item) => (
          <ItemCard key={item.itemId} item={item} />
        ))}
      </div>

      {/* 바닥 감지용 요소 + 로딩/끝 표시 */}
      <div ref={ref} className="grid place-items-center py-6">
        {isFetchingNextPage && <Spinner className="h-6 w-6" />}
        {!hasNextPage && items.length > 0 && (
          <span className="text-sm text-text-3">모두 불러왔어요</span>
        )}
      </div>
    </div>
  );
};

export default Items;
