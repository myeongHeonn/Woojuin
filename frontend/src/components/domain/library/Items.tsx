import { useIntersectionObserver } from '@/hooks/useIntersectionObserver';
import { useItems } from '@/hooks/useItems';
import { useParams } from 'react-router-dom';
import { useCallback } from 'react';

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
    <div>
      {/* {여기에 items들이 온다.} */}
      {items.map((item) => (
        <div key={item.itemId}>{/* 아이템 카드 */}</div>
      ))}
      <div ref={ref} />
    </div>
  );
};

export default Items;
