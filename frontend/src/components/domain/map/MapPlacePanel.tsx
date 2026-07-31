import { useEffect, useMemo, useRef, useState } from 'react';
import spacemanNoBg from '@/assets/spacemans/spaceman_no_bg.png';
import { MAP_ITEM_TYPES, MAP_ITEM_TYPE_COLOR, MAP_ITEM_TYPE_LABEL } from '@/constants/map';
import type { Category } from '@/types/category';
import type { MapCategoryId, MapPlace } from '@/types/map';
import { classNames } from '@/utils/classNames';

interface MapPlacePanelProps {
  categories: Category[];
  places: MapPlace[];
  /** 필터 결과가 아니라 지도 장소 데이터 자체가 비어 있는지 */
  isEmpty: boolean;
  /** 선택된 카테고리 ID. 비어 있으면 대시보드와 동일하게 '전체'만 선택 상태다. */
  selectedCategories: ReadonlySet<MapCategoryId>;
  favoriteActive: boolean;
  collapsed: boolean;
  selectedPlaceId: number | null;
  onCollapsedChange: (collapsed: boolean) => void;
  onToggleCategory: (categoryId: MapCategoryId | 'all') => void;
  onToggleFavorite: () => void;
  onSelectPlace: (placeId: number) => void;
  onOpenItem: (itemId: number) => void;
}

const MapPlacePanel = ({
  categories,
  places,
  isEmpty,
  selectedCategories,
  favoriteActive,
  collapsed,
  selectedPlaceId,
  onCollapsedChange,
  onToggleCategory,
  onToggleFavorite,
  onSelectPlace,
  onOpenItem,
}: MapPlacePanelProps) => {
  const dragStartYRef = useRef<number | null>(null);
  const ignoreClickRef = useRef(false);
  const categoryScrollRef = useRef<HTMLDivElement>(null);
  const [canScrollLeft, setCanScrollLeft] = useState(false);
  const [canScrollRight, setCanScrollRight] = useState(false);
  const categoryById = useMemo(
    () => new Map(categories.map((category) => [category.categoryId, category])),
    [categories],
  );
  const allSelected = selectedCategories.size === 0;

  useEffect(() => {
    const element = categoryScrollRef.current;
    if (!element) return;

    const updateScrollControls = () => {
      setCanScrollLeft(element.scrollLeft > 1);
      setCanScrollRight(element.scrollLeft + element.clientWidth < element.scrollWidth - 1);
    };
    const resizeObserver = new ResizeObserver(updateScrollControls);

    element.addEventListener('scroll', updateScrollControls, { passive: true });
    resizeObserver.observe(element);
    updateScrollControls();

    return () => {
      element.removeEventListener('scroll', updateScrollControls);
      resizeObserver.disconnect();
    };
  }, [isEmpty]);

  const scrollCategories = (direction: -1 | 1) => {
    const element = categoryScrollRef.current;
    if (!element) return;

    element.scrollBy({
      left: direction * element.clientWidth * 0.75,
      behavior: 'smooth',
    });
  };

  const handlePointerDown = (event: React.PointerEvent<HTMLButtonElement>) => {
    dragStartYRef.current = event.clientY;
    event.currentTarget.setPointerCapture(event.pointerId);
  };

  const handlePointerUp = (event: React.PointerEvent<HTMLButtonElement>) => {
    const startY = dragStartYRef.current;
    dragStartYRef.current = null;
    if (startY === null) return;

    const distance = event.clientY - startY;
    if (Math.abs(distance) < 24) return;

    ignoreClickRef.current = true;
    onCollapsedChange(distance > 0);
  };

  const handleToggle = () => {
    if (ignoreClickRef.current) {
      ignoreClickRef.current = false;
      return;
    }
    onCollapsedChange(!collapsed);
  };

  return (
    <aside
      aria-label="저장한 장소"
      data-testid="map-place-panel"
      className={classNames(
        'absolute z-[4] flex flex-col overflow-hidden border border-border/55 shadow-float',
        'bg-sidebar/84 backdrop-blur-[14px] desktop:bg-sidebar/42 desktop:backdrop-blur-[8px]',
        'inset-x-3 bottom-[calc(88px+env(safe-area-inset-bottom))] rounded-lg transition-[height] duration-200 ease-out',
        collapsed ? 'h-[112px]' : 'h-[50%]',
        'desktop:inset-x-auto desktop:bottom-[26px] desktop:right-[26px] desktop:top-[84px] desktop:h-auto desktop:max-h-none desktop:w-[320px] desktop:rounded-lg',
      )}
    >
      <button
        type="button"
        aria-expanded={!collapsed}
        aria-label={collapsed ? '장소 목록 펼치기' : '장소 목록 접기'}
        onClick={handleToggle}
        onPointerDown={handlePointerDown}
        onPointerUp={handlePointerUp}
        onPointerCancel={() => {
          dragStartYRef.current = null;
          ignoreClickRef.current = false;
        }}
        className="flex h-7 shrink-0 touch-none items-center justify-center desktop:hidden"
      >
        <span
          aria-hidden="true"
          className="h-1 w-10 rounded-pill bg-text-3/70 transition-colors hover:bg-text-2"
        />
      </button>

      {isEmpty ? (
        <div
          className={classNames(
            'flex min-h-0 flex-1 flex-col items-center justify-center px-5 text-center',
            collapsed ? 'gap-2' : 'gap-5',
            'desktop:gap-5',
          )}
        >
          <img
            src={spacemanNoBg}
            alt=""
            aria-hidden
            draggable={false}
            className={classNames(
              'select-none opacity-90 desktop:h-28 desktop:w-28',
              collapsed ? 'h-10 w-10' : 'h-28 w-28',
            )}
          />
          <div className="space-y-1.5">
            <p
              className={classNames(
                'font-semibold text-text-1 desktop:text-base',
                collapsed ? 'text-xs' : 'text-base',
              )}
            >
              아직 지도에 표시할 장소가 없어요
            </p>
            <p
              className={classNames(
                'text-text-3 desktop:text-sm',
                collapsed ? 'text-[10px]' : 'text-sm',
              )}
            >
              위치 정보가 있는 링크·사진·메모를 저장하면 지도에서 한눈에 볼 수 있어요.
            </p>
          </div>
        </div>
      ) : (
        <>
          <header className="flex items-center bg-gradient-to-b from-sidebar/35 to-transparent px-4 pb-2 pt-3.5">
            <h2 className="text-sm font-extrabold text-text-1">저장한 장소</h2>
            <span className="ml-1.5 text-xs font-semibold text-text-3">{places.length}곳</span>
            <div
              aria-label="데이터 타입 색상 안내"
              className="ml-auto flex items-center gap-2 text-[10px] font-semibold text-text-3"
            >
              {MAP_ITEM_TYPES.map((type) => (
                <span key={type} className="inline-flex items-center gap-1 whitespace-nowrap">
                  <span
                    aria-hidden="true"
                    className="h-1.5 w-1.5 rounded-full"
                    style={{ backgroundColor: MAP_ITEM_TYPE_COLOR[type] }}
                  />
                  {MAP_ITEM_TYPE_LABEL[type]}
                </span>
              ))}
            </div>
          </header>

          <div className="flex shrink-0 items-start gap-1 px-2">
            {canScrollLeft && (
              <button
                type="button"
                aria-label="이전 카테고리 보기"
                onClick={() => scrollCategories(-1)}
                className="inline-flex h-[30px] w-[30px] shrink-0 items-center justify-center rounded-pill border border-border/80 bg-surface-3/95 text-base font-bold text-text-1 shadow-sm transition-colors hover:bg-surface-2"
              >
                ‹
              </button>
            )}

            <div
              ref={categoryScrollRef}
              aria-label="장소 카테고리"
              className="scrollbar-none flex min-w-0 flex-1 gap-1.5 overflow-x-auto pb-3"
            >
              <button
                type="button"
                aria-pressed={favoriteActive}
                onClick={onToggleFavorite}
                className={classNames(
                  'inline-flex shrink-0 items-center rounded-pill border px-3 py-1.5 text-xs font-bold transition-colors',
                  favoriteActive
                    ? 'border-accent bg-surface-2 text-text-1'
                    : 'border-border bg-transparent text-text-2 hover:text-text-1',
                )}
              >
                즐겨찾기
              </button>

              <button
                type="button"
                aria-pressed={allSelected}
                onClick={() => onToggleCategory('all')}
                className={classNames(
                  'inline-flex shrink-0 items-center rounded-pill border px-3 py-1.5 text-xs font-bold transition-colors',
                  allSelected
                    ? 'border-accent bg-surface-2 text-text-1'
                    : 'border-border bg-transparent text-text-2 hover:text-text-1',
                )}
              >
                전체
              </button>

              {categories.map((category) => {
                const active = selectedCategories.has(category.categoryId);

                return (
                  <button
                    key={category.categoryId}
                    type="button"
                    aria-pressed={active}
                    onClick={() => onToggleCategory(category.categoryId)}
                    className={classNames(
                      'inline-flex shrink-0 items-center gap-1.5 rounded-pill border px-3 py-1.5 text-xs font-bold transition-colors',
                      active
                        ? 'border-accent bg-surface-2 text-text-1'
                        : 'border-border bg-transparent text-text-2 hover:text-text-1',
                    )}
                  >
                    <span
                      aria-hidden="true"
                      className="h-[7px] w-[7px] rounded-full"
                      style={{ backgroundColor: category.color }}
                    />
                    {category.name}
                  </button>
                );
              })}
            </div>

            {canScrollRight && (
              <button
                type="button"
                aria-label="다음 카테고리 보기"
                onClick={() => scrollCategories(1)}
                className="inline-flex h-[30px] w-[30px] shrink-0 items-center justify-center rounded-pill border border-border/80 bg-surface-3/95 text-base font-bold text-text-1 shadow-sm transition-colors hover:bg-surface-2"
              >
                ›
              </button>
            )}
          </div>

          <div
            className={classNames(
              'min-h-0 flex-1 overflow-y-auto px-2 pb-3 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden',
              collapsed && 'hidden desktop:block',
            )}
          >
            {places.length === 0 ? (
              <div className="grid h-full min-h-24 place-items-center px-5 text-center text-xs text-text-3">
                표시할 카테고리를 선택해 주세요.
              </div>
            ) : (
              places.map((place) => {
                const categoryLabel = place.categoryIds
                  .map((categoryId) => categoryById.get(categoryId)?.name)
                  .filter((label): label is string => Boolean(label))
                  .map((label) => `#${label}`)
                  .join(' · ');
                const selected = place.itemId === selectedPlaceId;
                const title = place.title ?? '제목 없음';

                return (
                  <div
                    key={place.itemId}
                    className={classNames(
                      'flex w-full cursor-pointer items-center gap-3 rounded-[11px] border px-2.5 py-2.5 text-left transition-colors',
                      selected
                        ? 'border-border/90 bg-surface-2/90'
                        : 'border-transparent bg-sidebar/10 hover:border-border/60 hover:bg-surface-2/65',
                    )}
                  >
                    <button
                      type="button"
                      onClick={() => onSelectPlace(place.itemId)}
                      aria-pressed={selected}
                      className="flex min-w-0 flex-1 items-center gap-3 text-left"
                    >
                      <span
                        aria-hidden="true"
                        className="h-[9px] w-[9px] shrink-0 rounded-full shadow-[0_0_3px_currentColor]"
                        style={{
                          backgroundColor: MAP_ITEM_TYPE_COLOR[place.type],
                          color: MAP_ITEM_TYPE_COLOR[place.type],
                        }}
                      />
                      <span className="min-w-0 flex-1">
                        <strong className="block truncate text-[13px] font-semibold text-text-1">
                          {title}
                        </strong>
                        <span className="mt-0.5 block truncate text-[11px] text-text-3">
                          {categoryLabel} · {MAP_ITEM_TYPE_LABEL[place.type]} · {place.address}
                        </span>
                      </span>
                    </button>
                    <button
                      type="button"
                      aria-label={`${title} 상세 보기`}
                      onClick={() => onOpenItem(place.itemId)}
                      className="shrink-0 rounded-md border border-border/70 px-2 py-1 text-[10px] font-bold text-text-2 transition-colors hover:bg-surface-3 hover:text-text-1"
                    >
                      상세
                    </button>
                  </div>
                );
              })
            )}
          </div>
        </>
      )}
    </aside>
  );
};

export default MapPlacePanel;
