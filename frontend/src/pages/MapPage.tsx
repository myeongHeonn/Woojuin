import { useMemo, useState } from 'react';
import MapCanvas from '@/components/domain/map/MapCanvas';
import MapPlacePanel from '@/components/domain/map/MapPlacePanel';
import { useStageMeta } from '@/hooks/useStageMeta';
import { MAP_CATEGORIES, MAP_PLACES, selectVisibleMapPlaces } from '@/stores/mock/map';
import type { MapCategoryId } from '@/types/map';

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
  const [activeCategories, setActiveCategories] = useState<Set<MapCategoryId>>(
    () => new Set(MAP_CATEGORIES.map((category) => category.id)),
  );
  const [isMapPanelCollapsed, setIsMapPanelCollapsed] = useState(isMobileViewport);
  const [selectedPlaceId, setSelectedPlaceId] = useState<number | null>(null);

  const visiblePlaces = useMemo(
    () => selectVisibleMapPlaces(MAP_PLACES, activeCategories),
    [activeCategories],
  );

  useStageMeta(`${visiblePlaces.length} places · ${activeCategories.size} categories`);

  const toggleCategory = (categoryId: MapCategoryId | 'all') => {
    let next: Set<MapCategoryId>;
    if (categoryId === 'all') {
      next =
        activeCategories.size === MAP_CATEGORIES.length
          ? new Set<MapCategoryId>()
          : new Set(MAP_CATEGORIES.map((category) => category.id));
    } else {
      next = new Set(activeCategories);
      if (next.has(categoryId)) {
        next.delete(categoryId);
      } else {
        next.add(categoryId);
      }
    }
    setActiveCategories(next);

    const selectedPlace = MAP_PLACES.find((place) => place.id === selectedPlaceId);
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
        places={visiblePlaces}
        selectedPlaceId={selectedPlaceId}
        onSelectPlace={selectPlaceAndCollapsePanel}
      />
      <MapPlacePanel
        places={visiblePlaces}
        activeCategories={activeCategories}
        collapsed={isMapPanelCollapsed}
        selectedPlaceId={selectedPlaceId}
        onCollapsedChange={setIsMapPanelCollapsed}
        onToggleCategory={toggleCategory}
        onSelectPlace={selectPlaceAndCollapsePanel}
      />
    </div>
  );
};

export default MapPage;
