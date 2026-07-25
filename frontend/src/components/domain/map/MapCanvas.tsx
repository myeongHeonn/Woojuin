import { MAP_CATEGORIES, MAP_ITEM_TYPE_LABEL } from '@/stores/mock/map';
import type { MapPlace } from '@/types/map';
import { classNames } from '@/utils/classNames';

interface MapCanvasProps {
  places: MapPlace[];
  selectedPlaceId: number | null;
  onSelectPlace: (placeId: number) => void;
}

const categoryById = new Map(MAP_CATEGORIES.map((category) => [category.id, category]));

/**
 * 실제 지도 SDK를 붙이기 전의 지도 자리.
 *
 * 지도 영역과 핀 상호작용만 먼저 고정하고, 나중에 지도 어댑터가 정해지면
 * 이 컴포넌트 내부만 교체한다. 페이지와 오른쪽 목록은 그대로 유지한다.
 */
const MapCanvas = ({ places, selectedPlaceId, onSelectPlace }: MapCanvasProps) => {
  const selectedPlace = places.find((place) => place.id === selectedPlaceId);
  const selectedCategory = selectedPlace ? categoryById.get(selectedPlace.categoryId) : undefined;

  return (
    <section
      aria-label="저장 장소 지도"
      data-testid="map-canvas"
      className="absolute inset-0 overflow-hidden bg-space"
    >
      <div className="pointer-events-none absolute inset-0 grid place-items-center">
        <span className="-translate-y-[22vh] text-4xl font-extrabold tracking-[-0.03em] text-text-3/45 desktop:translate-y-0 desktop:text-5xl">
          지도
        </span>
      </div>

      {places.map((place) => {
        const category = categoryById.get(place.categoryId);
        const selected = place.id === selectedPlaceId;

        return (
          <button
            key={place.id}
            type="button"
            aria-label={`${place.title} 지도 핀`}
            aria-pressed={selected}
            onClick={() => onSelectPlace(place.id)}
            style={{
              left: `${place.position.x}%`,
              top: `${place.position.y}%`,
              backgroundColor: category?.color,
              boxShadow: selected ? `0 0 0 5px ${category?.color}33` : undefined,
            }}
            className={classNames(
              'absolute z-[2] h-4 w-4 -translate-x-1/2 -translate-y-1/2 cursor-pointer rounded-full',
              'border-[2.5px] border-white/90 transition-transform hover:scale-125',
              selected && 'scale-125',
            )}
          />
        );
      })}

      {selectedPlace && selectedCategory && (
        <button
          type="button"
          onClick={() => onSelectPlace(selectedPlace.id)}
          style={{
            left: `${selectedPlace.position.x}%`,
            top: `${selectedPlace.position.y}%`,
          }}
          className="absolute z-[3] w-[220px] -translate-x-1/2 -translate-y-[calc(100%+18px)] cursor-pointer rounded-[14px] border border-border bg-surface px-4 py-3 text-left shadow-tooltip"
        >
          <span className="inline-flex items-center gap-1.5 rounded-pill bg-surface-3 px-2.5 py-1 text-[11px] font-bold text-text-1">
            <span
              aria-hidden="true"
              className="h-1.5 w-1.5 rounded-full"
              style={{ backgroundColor: selectedCategory.color }}
            />
            #{selectedCategory.label}
            <span className="font-semibold text-text-3">
              · {MAP_ITEM_TYPE_LABEL[selectedPlace.type]}
            </span>
          </span>
          <strong className="mt-2 block truncate text-[13.5px] text-text-1">
            {selectedPlace.title}
          </strong>
          <span className="mt-1 block truncate text-xs text-text-2">
            {selectedPlace.description}
          </span>
        </button>
      )}
    </section>
  );
};

export default MapCanvas;
