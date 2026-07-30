import { useCallback } from 'react';
import { useParams } from 'react-router-dom';
import { useSearch } from '@/hooks/useSearch';
import { useIntersectionObserver } from '@/hooks/useIntersectionObserver';
import ItemCard from './ItemCard';
import SearchMeta from '@/components/domain/search/SearchMeta';
import Spinner from '@/components/ui/Spinner';

interface SearchItemsProps {
  q: string;
  aiMode: boolean;
  onOpenItem?: (itemId: number) => void;
}

/**
 * 대시보드 검색 결과 — useSearch 로 받아 ItemCard 그리드로(목록과 같은 카드 재사용).
 * 로딩엔 스피너, 0건엔 안내, 상단엔 SearchMeta(부분일치·AI 해석). 무한스크롤은 목록과 동일.
 */
const SearchItems = ({ q, aiMode, onOpenItem }: SearchItemsProps) => {
  const { workspaceId } = useParams<{ workspaceId: string }>();
  const {
    items,
    isLoading,
    isFetchingNextPage,
    hasNextPage,
    fetchNextPage,
    partialMatch,
    interpretedQuery,
    aiPlanned,
  } = useSearch(Number(workspaceId), q, aiMode);

  const onIntersect = useCallback(() => {
    if (hasNextPage && !isFetchingNextPage) fetchNextPage();
  }, [hasNextPage, isFetchingNextPage, fetchNextPage]);
  const ref = useIntersectionObserver(onIntersect);

  if (isLoading) {
    return (
      <div className="grid place-items-center py-24">
        <Spinner className="h-6 w-6" />
      </div>
    );
  }

  return (
    <div className="pt-2 pb-24">
      <SearchMeta
        className="pl-9"
        partialMatch={partialMatch}
        interpretedQuery={interpretedQuery}
        aiPlanned={aiPlanned}
      />

      {items.length === 0 ? (
        <p className="py-24 text-center text-sm text-text-3">검색 결과가 없어요</p>
      ) : (
        <div
          className="mt-3 grid justify-center gap-x-[22px] gap-y-[30px]"
          style={{ gridTemplateColumns: 'repeat(auto-fill, 124px)' }}
        >
          {items.map((item) => (
            <ItemCard key={item.itemId} item={item} onClick={() => onOpenItem?.(item.itemId)} />
          ))}
        </div>
      )}

      <div ref={ref} className="grid place-items-center py-6">
        {isFetchingNextPage && <Spinner className="h-6 w-6" />}
      </div>
    </div>
  );
};

export default SearchItems;
