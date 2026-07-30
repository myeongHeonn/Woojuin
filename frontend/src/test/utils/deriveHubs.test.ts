import { describe, expect, it } from 'vitest';
import type { Constellation, Star } from '@/types/universe';
import { deriveHubs } from '@/utils/deriveHubs';

const star = (id: number, position: [number, number, number]): Star => ({
  id,
  position,
  title: `아이템 ${id}`,
  type: 'MEMO',
});

const constellation = (items: Star[]): Constellation => ({
  categoryId: 1,
  categoryName: '여행',
  color: 0x8fb4ff,
  items,
});

describe('카테고리 허브 생성', () => {
  it('아이템이 하나면 겹치는 카테고리 허브를 만들지 않는다', () => {
    expect(deriveHubs([constellation([star(1, [3, 4, 5])])])).toEqual([]);
  });

  it('아이템이 두 개 이상이면 중심 좌표에 카테고리 허브를 만든다', () => {
    const [hub] = deriveHubs([constellation([star(1, [2, 4, 6]), star(2, [4, 8, 10])])]);

    expect(hub.position).toEqual([3, 6, 8]);
    expect(hub.itemCount).toBe(2);
  });
});
