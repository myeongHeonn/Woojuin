import { useEffect, useMemo, useRef, useState } from 'react';
import { useParams } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import MapCanvas from '@/components/domain/map/MapCanvas';
import MapPlacePanel from '@/components/domain/map/MapPlacePanel';
import ItemModal from '@/components/domain/library/detail/ItemModal';
import { useCategories } from '@/hooks/useCategories';
import { useItems, processingPollInterval } from '@/hooks/useItems';
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
  const queryClient = useQueryClient();
  const { data: itemsData } = useItems({ workspaceId, size: 20 });
  const mapPollInterval = processingPollInterval(itemsData?.pages);
  const { data: places = [] } = useMapPlaces(workspaceId, mapPollInterval);

  // 처리 완료 직후 위치가 폴링 간격보다 늦게 반영될 수 있어, 처리가 끝나는 순간 한 번 더 새로고침한다.
  const wasProcessingRef = useRef(false);
  useEffect(() => {
    const processing = mapPollInterval !== false;
    if (wasProcessingRef.current && !processing) {
      queryClient.invalidateQueries({ queryKey: ['map-places', workspaceId] });
    }
    wasProcessingRef.current = processing;
  }, [mapPollInterval, queryClient, workspaceId]);

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

  const activeCategories = useMemo(
    () =>
      selectedCategories.length === 0
        ? new Set(categories.map((category) => category.categoryId))
        : new Set(selectedCategories),
    [categories, selectedCategories],
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
      />
      <MapPlacePanel
        categories={sortedFilterCategories}
        places={visiblePlaces}
        isEmpty={places.length === 0}
        activeCategories={activeCategories}
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
