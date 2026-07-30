import { describe, expect, it } from 'vitest';
import { toggleCategorySelection } from '@/utils/categorySelection';

describe('카테고리 선택', () => {
  it('전체 상태에서 카테고리를 누르면 해당 카테고리 하나만 선택한다', () => {
    expect(toggleCategorySelection([], 2)).toEqual([2]);
  });

  it('다른 카테고리를 누르면 기존 선택에 추가한다', () => {
    expect(toggleCategorySelection([2], 3)).toEqual([2, 3]);
  });

  it('선택된 카테고리를 다시 누르면 해제하고 마지막 해제는 전체 상태가 된다', () => {
    expect(toggleCategorySelection([2, 3], 2)).toEqual([3]);
    expect(toggleCategorySelection([3], 3)).toEqual([]);
  });

  it('전체를 누르면 항상 전체 상태를 유지한다', () => {
    expect(toggleCategorySelection([], 'all')).toEqual([]);
    expect(toggleCategorySelection([1, 2], 'all')).toEqual([]);
  });
});
