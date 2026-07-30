import { useParams } from 'react-router-dom';
import UniverseCanvas from '@/components/domain/universe/UniverseCanvas';
import ConstellationSearch from '@/components/domain/search/ConstellationSearch';
import { useStageMeta } from '@/hooks/useStageMeta';
import { useItems } from '@/hooks/useItems';
import { useCategories } from '@/hooks/useCategories';
import { MOCK_UNIVERSE } from '@/stores/mock/universe';

/**
 * 성좌 뷰.
 *
 * 헤더 요약(memories·constellations)은 실데이터다 — memories = 아이템 총개수(totalElements),
 * constellations = 카테고리(별자리) 수. 별 데이터(캔버스)는 아직 목업이다.
 * TODO(#11): 캔버스 별도 useUniverse(workspaceId) 로 교체한다.
 */
const UniversePage = () => {
  const { workspaceId } = useParams<{ workspaceId: string }>();
  const universe = MOCK_UNIVERSE;

  // 헤더 숫자는 기존 쿼리를 재사용해 구한다(전용 통계 API 없이).
  const { data: itemsData } = useItems({ workspaceId: Number(workspaceId), size: 20 });
  const { data: categories = [], isSuccess: categoriesLoaded } = useCategories(Number(workspaceId));

  // 둘 다 준비되기 전엔 요약을 비워 "0 memories · 0 constellations" 깜빡임을 막는다
  const memories = itemsData?.pages[0]?.totalElements;
  useStageMeta(
    memories !== undefined && categoriesLoaded
      ? `${memories} memories · ${categories.length} constellations`
      : undefined,
  );

  return (
    <>
      <UniverseCanvas data={universe} />
      <ConstellationSearch />
    </>
  );
};

export default UniversePage;
