import type { MapCategoryId, MapPlace } from '@/types/map';

export const selectVisibleMapPlaces = (
  places: MapPlace[],
  activeCategories: ReadonlySet<MapCategoryId>,
): MapPlace[] =>
  places.flatMap((place) => {
    const categoryIds = place.categoryIds.filter((categoryId) => activeCategories.has(categoryId));
    return categoryIds.length > 0 ? [{ ...place, categoryIds }] : [];
  });
