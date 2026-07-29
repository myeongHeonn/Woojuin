import { useEffect, useMemo, useRef, useState } from 'react';
import { useParams } from 'react-router-dom';
import MapCanvas from '@/components/domain/map/MapCanvas';
import MapPlacePanel from '@/components/domain/map/MapPlacePanel';
import ItemModal from '@/components/domain/library/detail/ItemModal';
import { useCategories } from '@/hooks/useCategories';
import { useMapPlaces } from '@/hooks/useMapPlaces';
import { useStageMeta } from '@/hooks/useStageMeta';
import type { MapCategoryId } from '@/types/map';
import { selectVisibleMapPlaces } from '@/utils/mapPlaces';

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
  const { data: places = [] } = useMapPlaces(workspaceId);
  const initializedWorkspaceRef = useRef<number | null>(null);
  const [activeCategories, setActiveCategories] = useState<Set<MapCategoryId>>(new Set());
  const [isMapPanelCollapsed, setIsMapPanelCollapsed] = useState(isMobileViewport);
  const [selectedPlaceId, setSelectedPlaceId] = useState<number | null>(null);
  const [openItemId, setOpenItemId] = useState<number | null>(null);

  useEffect(() => {
    if (!categoriesLoaded || initializedWorkspaceRef.current === workspaceId) return;

    initializedWorkspaceRef.current = workspaceId;
    setActiveCategories(new Set(categories.map((category) => category.categoryId)));
    setSelectedPlaceId(null);
    setOpenItemId(null);
  }, [categories, categoriesLoaded, workspaceId]);

  const visiblePlaces = useMemo(
    () => selectVisibleMapPlaces(places, activeCategories),
    [activeCategories, places],
  );

  useStageMeta(`${visiblePlaces.length} places · ${activeCategories.size} categories`);

  const toggleCategory = (categoryId: MapCategoryId | 'all') => {
    let next: Set<MapCategoryId>;
    if (categoryId === 'all') {
      next =
        activeCategories.size === categories.length
          ? new Set<MapCategoryId>()
          : new Set(categories.map((category) => category.categoryId));
    } else {
      next = new Set(activeCategories);
      if (next.has(categoryId)) {
        next.delete(categoryId);
      } else {
        next.add(categoryId);
      }
    }
    setActiveCategories(next);

    const selectedPlace = places.find((place) => place.itemId === selectedPlaceId);
    if (selectedPlace && !selectedPlace.categoryIds.some((id) => next.has(id))) {
      setSelectedPlaceId(null);
    }
  };

  const selectPlace = (placeId: number) => {
    setSelectedPlaceId((current) => (current === placeId ? null : placeId));
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
        categories={categories}
        places={visiblePlaces}
        activeCategories={activeCategories}
        collapsed={isMapPanelCollapsed}
        selectedPlaceId={selectedPlaceId}
        onCollapsedChange={setIsMapPanelCollapsed}
        onToggleCategory={toggleCategory}
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
