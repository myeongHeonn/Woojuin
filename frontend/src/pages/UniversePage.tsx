import { useEffect, useRef, useState } from 'react';
import { useParams } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { useAtomValue, useSetAtom } from 'jotai';
import UniverseCanvas from '@/components/domain/universe/UniverseCanvas';
import ConstellationSearch from '@/components/domain/search/ConstellationSearch';
import ProcessingBadge from '@/components/domain/stage/ProcessingBadge';
import ItemModal from '@/components/domain/library/detail/ItemModal';
import Spinner from '@/components/ui/Spinner';
import { useStageMeta } from '@/hooks/useStageMeta';
import { processingPollInterval, processingItemCount, useItems } from '@/hooks/useItems';
import { useCategories } from '@/hooks/useCategories';
import { universeKey, useUniverse } from '@/hooks/useUniverse';
import { tutorialActiveAtom, tutorialFixtureVisibleAtom } from '@/stores/tutorialAtoms';
import {
  isUniverseEmpty,
  tutorialUniverseFixture,
} from '@/components/domain/tutorial/tutorialUniverseFixture';

/**
 * 성좌 뷰.
 *
 * 헤더 요약과 3D 좌표를 모두 서버에서 받는다. 아이템 처리 중에는 목록 상태를 기준으로
 * 우주 API도 폴링하고, 처리가 끝나는 순간 한 번 더 갱신해 새 좌표를 놓치지 않는다.
 */
const UniversePage = () => {
  const { workspaceId: workspaceIdParam } = useParams<{ workspaceId: string }>();
  const workspaceId = Number(workspaceIdParam);
  const queryClient = useQueryClient();
  const [openItemId, setOpenItemId] = useState<number | null>(null);
  const isTutorialActive = useAtomValue(tutorialActiveAtom);
  const setTutorialFixtureVisible = useSetAtom(tutorialFixtureVisibleAtom);
  const [highlightItemIds, setHighlightItemIds] = useState<number[]>([]);
  const [activeCategoryId, setActiveCategoryId] = useState<number | null>(null);

  // 헤더 숫자와 PROCESSING 여부는 기존 목록 쿼리를 재사용한다(전용 통계 API 없음).
  const { data: itemsData } = useItems({ workspaceId, size: 20 });
  const { data: categories = [], isSuccess: categoriesLoaded } = useCategories(workspaceId);
  const universePollInterval = processingPollInterval(itemsData?.pages);
  const processingCount = processingItemCount(itemsData?.pages);
  const {
    data: universe,
    isLoading: isUniverseLoading,
    isError: isUniverseError,
    refetch: refetchUniverse,
  } = useUniverse(workspaceId, universePollInterval);

  const wasProcessingRef = useRef(false);
  useEffect(() => {
    const processing = universePollInterval !== false;
    if (wasProcessingRef.current && !processing) {
      queryClient.invalidateQueries({ queryKey: universeKey(workspaceId) });
    }
    wasProcessingRef.current = processing;
  }, [queryClient, universePollInterval, workspaceId]);

  // 둘 다 준비되기 전엔 요약을 비워 "0 memories · 0 constellations" 깜빡임을 막는다
  const memories = itemsData?.pages[0]?.totalElements;
  useStageMeta(
    memories !== undefined && categoriesLoaded
      ? `${memories} memories · ${categories.length} constellations`
      : undefined,
  );

  const showTutorialFixture = Boolean(isTutorialActive && universe && isUniverseEmpty(universe));
  const displayedUniverse = showTutorialFixture ? tutorialUniverseFixture : universe;

  useEffect(() => {
    setTutorialFixtureVisible(showTutorialFixture);
    return () => setTutorialFixtureVisible(false);
  }, [setTutorialFixtureVisible, showTutorialFixture]);

  return (
    <div className="relative h-full w-full overflow-hidden bg-space">
      {displayedUniverse && (
        <UniverseCanvas
          data={displayedUniverse}
          highlightItemIds={highlightItemIds}
          activeCategoryId={activeCategoryId}
          onSelectConstellation={(catId) =>
            setActiveCategoryId((prev) => (prev === catId ? null : catId))
          }
          onOpenItem={showTutorialFixture ? undefined : setOpenItemId}
        />
      )}

      {isUniverseLoading && (
        <div className="absolute inset-0 grid place-items-center">
          <Spinner className="h-7 w-7" />
        </div>
      )}

      {isUniverseError && (
        <div className="absolute inset-0 grid place-items-center px-6 text-center">
          <div>
            <p className="text-sm text-text-2">우주를 불러오지 못했어요.</p>
            <button
              type="button"
              onClick={() => refetchUniverse()}
              className="mt-3 rounded-lg border border-border px-3 py-2 text-xs text-text-1"
            >
              다시 시도
            </button>
          </div>
        </div>
      )}

      <ConstellationSearch
        onSearchResults={setHighlightItemIds}
        aboveBar={<ProcessingBadge count={processingCount} label="별 만드는 중" />}
      />
      <ItemModal
        workspaceId={workspaceId}
        itemId={openItemId}
        onClose={() => setOpenItemId(null)}
      />
    </div>
  );
};

export default UniversePage;
