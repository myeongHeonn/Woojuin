import { useEffect, useMemo, useRef, useState } from 'react';
import { useParams } from 'react-router-dom';
import MapCanvas from '@/components/domain/map/MapCanvas';
import MapPlacePanel from '@/components/domain/map/MapPlacePanel';
import ItemModal from '@/components/domain/library/detail/ItemModal';
import { useCategories } from '@/hooks/useCategories';
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
  const { data: places = [] } = useMapPlaces(workspaceId);
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
