export function toggleCategorySelection(selected: number[], categoryId: number | 'all'): number[] {
  if (categoryId === 'all') return [];

  return selected.includes(categoryId)
    ? selected.filter((selectedId) => selectedId !== categoryId)
    : [...selected, categoryId];
}
