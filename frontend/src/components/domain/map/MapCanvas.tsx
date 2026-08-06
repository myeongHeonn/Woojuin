import { useEffect, useMemo, useRef } from 'react';
import { useAtomValue } from 'jotai';
import 'maplibre-gl/dist/maplibre-gl.css';
import type { MapAdapter } from '@/components/domain/map/mapAdapter';
import { createOpenFreeMapAdapter } from '@/components/domain/map/openFreeMapAdapter';
import { MAP_ITEM_TYPE_COLOR, MAP_ITEM_TYPE_LABEL } from '@/constants/map';
import { themeAtom } from '@/stores/themeAtoms';
import type { Category } from '@/types/category';
import type { MapPlace } from '@/types/map';

interface MapCanvasProps {
  categories: Category[];
  places: MapPlace[];
  selectedPlaceId: number | null;
  onSelectPlace: (placeId: number) => void;
  onOpenItem: (itemId: number) => void;
  onDeselectPlace: () => void;
  onResetView: () => void;
}

const MapCanvas = ({
  categories,
  places,
  selectedPlaceId,
  onSelectPlace,
  onOpenItem,
  onDeselectPlace,
  onResetView,
}: MapCanvasProps) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const adapterRef = useRef<MapAdapter | null>(null);
  const theme = useAtomValue(themeAtom);
  // 생성 이펙트는 deps 가 비어 있어야 한다(지도를 다시 만들면 카메라·마커가 날아간다).
  // 그래서 첫 테마는 ref 로 넘기고, 이후 변경은 아래 별도 이펙트가 setTheme 으로 전한다.
  const themeRef = useRef(theme);
  themeRef.current = theme;
  const onSelectPlaceRef = useRef(onSelectPlace);
  const onOpenItemRef = useRef(onOpenItem);
  const onDeselectPlaceRef = useRef(onDeselectPlace);
  const onResetViewRef = useRef(onResetView);
  onSelectPlaceRef.current = onSelectPlace;
  onOpenItemRef.current = onOpenItem;
  onDeselectPlaceRef.current = onDeselectPlace;
  onResetViewRef.current = onResetView;
  const categoryById = useMemo(
    () => new Map(categories.map((category) => [category.categoryId, category])),
    [categories],
  );

  const points = useMemo(
    () =>
      places.map((place) => {
        const categoryLabel = place.categoryIds
          .map((categoryId) => categoryById.get(categoryId)?.name)
          .filter((label): label is string => Boolean(label))
          .join(' · #');

        return {
          id: place.itemId,
          lat: place.lat,
          lng: place.lng,
          title: place.title ?? '제목 없음',
          address: place.address,
          categoryLabel: categoryLabel || '미분류',
          typeLabel: MAP_ITEM_TYPE_LABEL[place.type],
          color: MAP_ITEM_TYPE_COLOR[place.type],
        };
      }),
    [categoryById, places],
  );

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    const adapter = createOpenFreeMapAdapter({
      container,
      theme: themeRef.current,
      onSelectPoint: (pointId) => onSelectPlaceRef.current(pointId),
      onOpenPoint: (pointId) => onOpenItemRef.current(pointId),
      onDeselectPoint: () => onDeselectPlaceRef.current(),
      onResetView: () => onResetViewRef.current(),
    });
    const resizeObserver = new ResizeObserver(() => adapter.resize());

    adapterRef.current = adapter;
    resizeObserver.observe(container);

    return () => {
      resizeObserver.disconnect();
      adapter.destroy();
      adapterRef.current = null;
    };
  }, []);

  useEffect(() => {
    adapterRef.current?.setPoints(points);
  }, [points]);

  useEffect(() => {
    adapterRef.current?.selectPoint(selectedPlaceId);
  }, [selectedPlaceId]);

  useEffect(() => {
    adapterRef.current?.setTheme(theme);
  }, [theme]);

  return (
    <section
      aria-label="저장한 장소 지도"
      data-testid="map-canvas"
      data-tutorial-page-content="map"
      className="absolute inset-0 overflow-hidden bg-space"
    >
      <div className="woojuin-open-free-map absolute inset-0">
        <div ref={containerRef} className="h-full w-full" />
      </div>
      {/* 좌우 가장자리를 앱 배경 쪽으로 살짝 눕히는 비네트. 색을 --color-space 에서 뽑으므로
          라이트에서는 밝게 깔린다 — 다크 값으로 두면 밝은 지도 위에 때가 낀 것처럼 보인다.
          (규칙은 index.css 의 .woojuin-map-vignette) */}
      <div className="woojuin-map-vignette pointer-events-none absolute inset-0" />
      <div className="pointer-events-none absolute bottom-above-tabbar left-4 rounded-pill border border-border/70 bg-sidebar/65 px-3 py-1.5 text-[11px] font-semibold text-text-2 backdrop-blur-md desktop:bottom-5 desktop:left-5">
        OpenFreeMap · 장소 {places.length}곳
      </div>
    </section>
  );
};

export default MapCanvas;
