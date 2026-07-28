import { describe, expect, it } from 'vitest';
import { MAP_CATEGORIES, MAP_PLACES, selectVisibleMapPlaces } from '@/stores/mock/map';
import { MOCK_UNIVERSE } from '@/stores/mock/universe';

describe('지도 목업 장소', () => {
  it('여러 카테고리에 포함된 동일 item id를 지도 장소 하나로 만든다', () => {
    const duplicatedItems = MOCK_UNIVERSE.constellations
      .flatMap((constellation) => constellation.items)
      .filter((item) => item.id === 21);
    const mapPlaces = MAP_PLACES.filter((place) => place.itemId === 21);

    expect(duplicatedItems).toHaveLength(8);
    expect(mapPlaces).toHaveLength(1);
    expect(mapPlaces[0].categoryIds).toEqual([1, 2, 3, 4, 5, 6, 8, 10]);
  });

  it('모든 지도 장소의 item id는 고유하다', () => {
    const placeIds = MAP_PLACES.map((place) => place.itemId);

    expect(new Set(placeIds).size).toBe(placeIds.length);
  });

  it('모바일 카테고리 가로 스크롤을 확인할 수 있는 개수의 카테고리를 제공한다', () => {
    expect(MAP_CATEGORIES).toHaveLength(10);
  });

  it('연결된 카테고리 중 하나라도 활성화되면 장소를 표시한다', () => {
    const seongsuPlaces = selectVisibleMapPlaces(MAP_PLACES, new Set([8]));
    const jejuPlaces = selectVisibleMapPlaces(MAP_PLACES, new Set([7]));

    expect(seongsuPlaces.find((place) => place.itemId === 21)?.categoryIds).toEqual([8]);
    expect(jejuPlaces.some((place) => place.itemId === 21)).toBe(false);
  });
});
