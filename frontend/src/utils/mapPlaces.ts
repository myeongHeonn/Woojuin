import type { Category } from '@/types/category';
import type { MapCategoryId, MapPlace } from '@/types/map';

export const sortCategoriesByPlaceCount = (
  categories: Category[],
  places: MapPlace[],
): Category[] => {
  const itemCountByCategory = new Map<MapCategoryId, number>();

  places.forEach((place) => {
    new Set(place.categoryIds).forEach((categoryId) => {
      itemCountByCategory.set(categoryId, (itemCountByCategory.get(categoryId) ?? 0) + 1);
    });
  });

  return categories
    .map((category, originalIndex) => ({
      category,
      originalIndex,
      itemCount: itemCountByCategory.get(category.categoryId) ?? 0,
    }))
    .sort(
      (left, right) => right.itemCount - left.itemCount || left.originalIndex - right.originalIndex,
    )
    .map(({ category }) => category);
};

export const selectVisibleMapPlaces = (
  places: MapPlace[],
  activeCategories: ReadonlySet<MapCategoryId>,
  favoriteOnly = false,
): MapPlace[] =>
  places.flatMap((place) => {
    if (favoriteOnly && !place.favorite) return [];

    const categoryIds = place.categoryIds.filter((categoryId) => activeCategories.has(categoryId));
    return categoryIds.length > 0 ? [{ ...place, categoryIds }] : [];
  });
