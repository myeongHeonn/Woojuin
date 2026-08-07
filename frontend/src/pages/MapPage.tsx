import { useEffect, useMemo, useRef, useState } from 'react';
import { useParams } from 'react-router-dom';
import MapCanvas from '@/components/domain/map/MapCanvas';
import MapPlacePanel from '@/components/domain/map/MapPlacePanel';
import MapLocationToast from '@/components/domain/map/MapLocationToast';
import ProcessingBadge from '@/components/domain/stage/ProcessingBadge';
import ItemModal from '@/components/domain/library/detail/ItemModal';
import { useCategories } from '@/hooks/useCategories';
import { useItems, processingPollInterval, processingItemCount } from '@/hooks/useItems';
import { useMapPlaces } from '@/hooks/useMapPlaces';
import { useStageMeta } from '@/hooks/useStageMeta';
import type { MapCategoryId } from '@/types/map';
import { toggleCategorySelection } from '@/utils/categorySelection';
import { selectVisibleMapPlaces, sortCategoriesByPlaceCount } from '@/utils/mapPlaces';

const isMobileViewport = () =>
  typeof window !== 'undefined' &&
  typeof window.matchMedia === 'function' &&
  window.matchMedia('(max-width: 639px)').matches;

/**
 * 지도 뷰 — 지도 자리 + 저장 장소 목록.
 *
 * 지도 SDK는 아직 정해지지 않았으므로 현재 MapCanvas는 화면 자리와 핀 상호작용만 맡는다.
 * 추후 지도 어댑터가 정해지면 MapCanvas 내부 구현만 교체한다.
 */
const MapPage = () => {
  const { workspaceId: workspaceIdParam } = useParams<{ workspaceId: string }>();
  const workspaceId = Number(workspaceIdParam);
  const { data: categories = [], isSuccess: categoriesLoaded } = useCategories(workspaceId);

  // 지도를 열어둔 채 새 장소를 저장하면 핀이 자동으로 떠야 한다.
  // 새 핀 = 처리 중인 아이템의 위치 추출이 끝나는 것 — 그 신호(status)는 지도 응답엔 없고
  // items 에만 있으므로, items 를 여기서 구독해 "처리 중" 동안 지도를 폴링한다.
  // (useItems 자신도 처리 중일 때만 폴링하므로 신호가 계속 신선하다)
  const { data: itemsData } = useItems({ workspaceId, size: 20 });
  const mapPollInterval = processingPollInterval(itemsData?.pages);
  const processingCount = processingItemCount(itemsData?.pages);
  const { data: places = [], refetch: refetchPlaces } = useMapPlaces(workspaceId, mapPollInterval);

  // 처리 중이던 아이템이 방금 끝난 것(삭제와 구분)을 잡아 지도를 즉시 새로고침하고,
  // 좌표를 못 찾아 핀으로 안 뜬 경우 왜 안 보이는지 짧게 알려준다(로딩 배지와 같은 자리
  // 토스트 — 위치 추출은 best-effort 라 애초에 장소가 아닌 링크는 이 알림 대상이 아니지만,
  // 지도 화면에서 저장한 만큼 "핀이 안 떴다"는 사실 자체는 알 가치가 있다).
  const prevProcessingIdsRef = useRef<Set<number>>(new Set());
  const [locationToast, setLocationToast] = useState<string | null>(null);
  const toastTimerRef = useRef<ReturnType<typeof setTimeout>>();

  useEffect(() => {
    const items = (itemsData?.pages ?? []).flatMap((page) => page.content);
    const currentProcessingIds = new Set(
      items.filter((item) => item.status === 'PROCESSING').map((item) => item.itemId),
    );
    const currentAllIds = new Set(items.map((item) => item.itemId));
    // 처리 중이었는데 지금은 아니고, 목록에서 사라진(삭제된) 것도 아니면 = 방금 완료됐다
    const justFinished = [...prevProcessingIdsRef.current].filter(
      (id) => !currentProcessingIds.has(id) && currentAllIds.has(id),
    );
    prevProcessingIdsRef.current = currentProcessingIds;
    if (justFinished.length === 0) return;

    refetchPlaces().then(({ data: freshPlaces }) => {
      const placeIds = new Set((freshPlaces ?? []).map((place) => place.itemId));
      const missing = justFinished.filter((id) => !placeIds.has(id));
      if (missing.length === 0) return;

      clearTimeout(toastTimerRef.current);
      setLocationToast(
        missing.length === 1
          ? '위치 정보가 없습니다.'
          : `${missing.length}개는 위치 정보가 없습니다.`,
      );
      toastTimerRef.current = setTimeout(() => setLocationToast(null), 1200);
    });
  }, [itemsData, refetchPlaces]);

  useEffect(() => () => clearTimeout(toastTimerRef.current), []);

  const initializedWorkspaceRef = useRef<number | null>(null);
  const [selectedCategories, setSelectedCategories] = useState<MapCategoryId[]>([]);
  const [favoriteOnly, setFavoriteOnly] = useState(false);
  const [isMapPanelCollapsed, setIsMapPanelCollapsed] = useState(isMobileViewport);
  const [selectedPlaceId, setSelectedPlaceId] = useState<number | null>(null);
  const [openItemId, setOpenItemId] = useState<number | null>(null);

  useEffect(() => {
    if (!categoriesLoaded || initializedWorkspaceRef.current === workspaceId) return;

    initializedWorkspaceRef.current = workspaceId;
    setSelectedCategories([]);
    setFavoriteOnly(false);
    setSelectedPlaceId(null);
    setOpenItemId(null);
  }, [categories, categoriesLoaded, workspaceId]);

  const selectedCategorySet = useMemo(() => new Set(selectedCategories), [selectedCategories]);
  const activeCategories = useMemo(
    () =>
      selectedCategorySet.size === 0
        ? new Set(categories.map((category) => category.categoryId))
        : selectedCategorySet,
    [categories, selectedCategorySet],
  );
  const visiblePlaces = useMemo(
    () => selectVisibleMapPlaces(places, activeCategories, favoriteOnly),
    [activeCategories, favoriteOnly, places],
  );
  const sortedFilterCategories = useMemo(
    () => sortCategoriesByPlaceCount(categories, places),
    [categories, places],
  );

  useStageMeta(`${visiblePlaces.length} places · ${activeCategories.size} categories`);

  const toggleCategory = (categoryId: MapCategoryId | 'all') => {
    const nextSelected = toggleCategorySelection(selectedCategories, categoryId);

    setSelectedCategories(nextSelected);
    const nextActive =
      nextSelected.length === 0
        ? new Set(categories.map((category) => category.categoryId))
        : new Set(nextSelected);

    const selectedPlace = places.find((place) => place.itemId === selectedPlaceId);
    if (selectedPlace && !selectedPlace.categoryIds.some((id) => nextActive.has(id))) {
      setSelectedPlaceId(null);
    }
  };

  const selectPlace = (placeId: number) => {
    setSelectedPlaceId((current) => (current === placeId ? null : placeId));
  };

  const toggleFavorite = () => {
    const next = !favoriteOnly;
    setFavoriteOnly(next);

    const selectedPlace = places.find((place) => place.itemId === selectedPlaceId);
    if (next && selectedPlace && !selectedPlace.favorite) {
      setSelectedPlaceId(null);
    }
  };

  const selectPlaceAndCollapsePanel = (placeId: number) => {
    selectPlace(placeId);
    if (isMobileViewport()) {
      setIsMapPanelCollapsed(true);
    }
  };

  const resetMapView = () => {
    setSelectedCategories([]);
    setSelectedPlaceId(null);
  };

  return (
    <div
      data-map-panel-collapsed={isMapPanelCollapsed}
      className="relative h-full w-full overflow-hidden bg-space"
    >
      <MapCanvas
        categories={categories}
        places={visiblePlaces}
        selectedPlaceId={selectedPlaceId}
        onSelectPlace={selectPlaceAndCollapsePanel}
        onOpenItem={setOpenItemId}
        onDeselectPlace={() => setSelectedPlaceId(null)}
        onResetView={resetMapView}
      />
      {/*
        헤더 왼쪽 아래, MapPlacePanel(우측/하단)과 안 겹치는 좌상단. 헤더 왼쪽에는
        (모바일) WorkspaceSwitcher + meta 두 줄, (데스크톱) 제목 + meta 두 줄이 쌓여
        있어 그 아래로 충분히 내려야 한다 — top-16/top-[90px] 정도로는 meta 줄과 겹쳤다.
        배지·토스트는 같은 자리에 세로로 쌓인다(위치 없음 토스트는 배지가 사라진 뒤에도
        잠깐 남을 수 있어 별개 조건으로 각자 렌더한다).
      */}
      <div className="absolute left-5 top-[calc(96px+var(--safe-top))] z-[6] flex flex-col items-start gap-2 desktop:left-[34px] desktop:top-[calc(112px+var(--safe-top))]">
        <ProcessingBadge count={processingCount} label="장소 찾는 중" />
        <MapLocationToast message={locationToast} />
      </div>
      <MapPlacePanel
        categories={sortedFilterCategories}
        places={visiblePlaces}
        isEmpty={places.length === 0}
        selectedCategories={selectedCategorySet}
        favoriteActive={favoriteOnly}
        collapsed={isMapPanelCollapsed}
        selectedPlaceId={selectedPlaceId}
        onCollapsedChange={setIsMapPanelCollapsed}
        onToggleCategory={toggleCategory}
        onToggleFavorite={toggleFavorite}
        onSelectPlace={selectPlaceAndCollapsePanel}
        onOpenItem={setOpenItemId}
      />
      <ItemModal
        workspaceId={workspaceId}
        itemId={openItemId}
        onClose={() => setOpenItemId(null)}
      />
    </div>
  );
};

export default MapPage;
