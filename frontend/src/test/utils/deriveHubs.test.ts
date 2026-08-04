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

  it('중심 좌표가 같은 카테고리 허브들은 서로 밀어내어 위치가 겹치지 않는다', () => {
    const c1: Constellation = {
      categoryId: 1,
      categoryName: '여행',
      color: 0x8fb4ff,
      items: [star(1, [0, 0, 0]), star(2, [2, 2, 2])],
    };
    const c2: Constellation = {
      categoryId: 2,
      categoryName: '일상',
      color: 0xff8fb4,
      items: [star(3, [0, 0, 0]), star(4, [2, 2, 2])],
    };

    const [hub1, hub2] = deriveHubs([c1, c2]);

    expect(hub1.position).not.toEqual(hub2.position);
    const dist = Math.hypot(
      hub2.position[0] - hub1.position[0],
      hub2.position[1] - hub1.position[1],
      hub2.position[2] - hub1.position[2],
    );
    expect(dist).toBeGreaterThanOrEqual(3.5);
  });
});
