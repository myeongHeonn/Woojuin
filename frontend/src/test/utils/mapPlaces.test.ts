import { describe, expect, it } from 'vitest';
import type { Category } from '@/types/category';
import type { MapPlace } from '@/types/map';
import { selectVisibleMapPlaces, sortCategoriesByPlaceCount } from '@/utils/mapPlaces';

const categories: Category[] = [
  { categoryId: 1, name: '첫 번째', color: '#111111' },
  { categoryId: 2, name: '두 번째', color: '#222222' },
  { categoryId: 3, name: '세 번째', color: '#333333' },
  { categoryId: 4, name: '네 번째', color: '#444444' },
];

const place = (itemId: number, categoryIds: number[], favorite = false): MapPlace => ({
  itemId,
  categoryIds,
  type: 'MEMO',
  title: `아이템 ${itemId}`,
  favorite,
  lat: 37.5,
  lng: 127,
  address: '테스트 주소',
});

describe('지도 카테고리 필터 정렬', () => {
  it('연결된 아이템 수가 많은 카테고리부터 정렬한다', () => {
    const places = [place(1, [2, 3]), place(2, [2]), place(3, [2, 3]), place(4, [1])];

    expect(
      sortCategoriesByPlaceCount(categories, places).map(({ categoryId }) => categoryId),
    ).toEqual([2, 3, 1, 4]);
  });

  it('아이템 수가 같으면 서버에서 받은 기존 순서를 유지한다', () => {
    const places = [place(1, [1]), place(2, [2]), place(3, [3])];

    expect(
      sortCategoriesByPlaceCount(categories, places).map(({ categoryId }) => categoryId),
    ).toEqual([1, 2, 3, 4]);
  });
});

describe('지도 아이템 카테고리 필터', () => {
  it('선택된 카테고리 중 하나라도 연결된 아이템을 표시한다', () => {
    const places = [place(1, [1, 2]), place(2, [3])];

    expect(selectVisibleMapPlaces(places, new Set([2]))).toEqual([
      { ...places[0], categoryIds: [2] },
    ]);
  });

  it('즐겨찾기 필터와 카테고리 필터를 함께 적용한다', () => {
    const places = [place(1, [1], true), place(2, [1]), place(3, [2], true)];

    expect(selectVisibleMapPlaces(places, new Set([1]), true)).toEqual([places[0]]);
  });
});
