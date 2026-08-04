import { type Constellation, type Vec3 } from '@/types/universe';

/**
 * 카테고리(별자리)의 위치·크기는 서버가 주지 않는다.
 * **소속 아이템들의 중심**을 카테고리 위치로 삼는다.
 * 아이템이 추가·삭제되면 별자리가 자연스럽게 그쪽으로 움직인다.
 */
export interface Hub {
  categoryId: number;
  name: string;
  color: number;
  /** 소속 아이템 좌표의 평균 */
  position: Vec3;
  /** 아이템 수에 비례 — 큰 별자리가 크게 보인다 */
  radius: number;
  itemCount: number;
}

/** 허브 크기 범위 (v3.5 목업의 1.5~2.7 을 따른다) */
const MIN_RADIUS = 1.5;
const MAX_RADIUS = 2.7;
/** 이 개수 이상이면 최대 크기 */
const RADIUS_SATURATION = 12;

function hubRadius(itemCount: number): number {
  const t = Math.min(itemCount, RADIUS_SATURATION) / RADIUS_SATURATION;
  return MIN_RADIUS + (MAX_RADIUS - MIN_RADIUS) * t;
}

/** 허브 간 최소 간격 — 허브 반지름과 여유 마진을 고려해 서로 겹치거나 부딪히지 않는 거리 */
const MIN_HUB_DISTANCE = 4.0;
const SEPARATION_ITERATIONS = 8;

/**
 * 허브 위치가 서로 겹치거나 너무 가까우면(MIN_HUB_DISTANCE 미만),
 * 결정론적(Deterministic) 3D 오프셋을 적용해 겹치지 않게 서로 밀어낸다.
 */
function separateHubs(hubs: Hub[]): void {
  for (let iter = 0; iter < SEPARATION_ITERATIONS; iter++) {
    let moved = false;
    for (let i = 0; i < hubs.length; i++) {
      for (let j = i + 1; j < hubs.length; j++) {
        const a = hubs[i];
        const b = hubs[j];
        let dx = b.position[0] - a.position[0];
        let dy = b.position[1] - a.position[1];
        let dz = b.position[2] - a.position[2];
        let dist = Math.hypot(dx, dy, dz);

        const requiredDist = Math.max(MIN_HUB_DISTANCE, a.radius + b.radius + 1.2);

        if (dist < requiredDist) {
          moved = true;
          // 두 허브 중심점이 완벽히 동일하면 인덱스 기반으로 결정론적 방향 생성
          if (dist < 0.001) {
            const angle = (i * 1.37 + j * 2.41) % (2 * Math.PI);
            dx = Math.cos(angle);
            dy = Math.sin(angle);
            dz = Math.cos(i + j);
            dist = Math.hypot(dx, dy, dz);
          }

          const overlap = (requiredDist - dist) / 2;
          const ux = (dx / dist) * overlap;
          const uy = (dy / dist) * overlap;
          const uz = (dz / dist) * overlap;

          a.position[0] -= ux;
          a.position[1] -= uy;
          a.position[2] -= uz;

          b.position[0] += ux;
          b.position[1] += uy;
          b.position[2] += uz;
        }
      }
    }
    if (!moved) break;
  }
}

/**
 * 아이템이 0개면 위치를 정할 수 없고, 1개면 허브가 아이템과 같은 좌표에 겹친다.
 * 따라서 카테고리 허브는 아이템이 2개 이상일 때만 만든다.
 * 카테고리 중심점이 같거나 가까운 허브는 3D 구면 미세 오프셋(separateHubs)으로 겹침을 방지한다.
 */
export function deriveHubs(constellations: Constellation[]): Hub[] {
  const hubs = constellations
    .filter((c) => c.items.length > 1)
    .map((c) => {
      const sum = c.items.reduce<Vec3>(
        (acc, item) => [
          acc[0] + item.position[0],
          acc[1] + item.position[1],
          acc[2] + item.position[2],
        ],
        [0, 0, 0],
      );
      const n = c.items.length;
      return {
        categoryId: c.categoryId,
        name: c.categoryName,
        color: c.color,
        position: [sum[0] / n, sum[1] / n, sum[2] / n] as Vec3,
        radius: hubRadius(n),
        itemCount: n,
      };
    });

  separateHubs(hubs);
  return hubs;
}

function distance(a: Hub, b: Hub): number {
  return Math.hypot(
    a.position[0] - b.position[0],
    a.position[1] - b.position[1],
    a.position[2] - b.position[2],
  );
}

/**
 * 별자리끼리 잇는 선 — **최소 신장 트리(MST)**.
 *
 * 모든 쌍을 이으면 N(N-1)/2 개(10개 → 45개)라 화면이 거미줄이 된다.
 * MST 는 정확히 N-1 개 선으로 **모든 별자리를 하나로 연결**하면서,
 * 가까운 것끼리 잇기 때문에 별자리다운 모양이 나온다.
 *
 * Prim 알고리즘 — 별자리 수가 수십 개 수준이라 O(N²) 로 충분하다.
 */
export function buildHubLinks(hubs: Hub[]): [number, number][] {
  if (hubs.length < 2) return [];

  const connected = new Set<number>([0]);
  const links: [number, number][] = [];

  // 연결된 무리에서 가장 가까운 바깥 별자리를 하나씩 끌어들인다
  while (connected.size < hubs.length) {
    let from = -1;
    let to = -1;
    let min = Infinity;

    for (const i of connected) {
      for (let j = 0; j < hubs.length; j++) {
        if (connected.has(j)) continue;
        const d = distance(hubs[i], hubs[j]);
        if (d < min) {
          min = d;
          from = i;
          to = j;
        }
      }
    }

    links.push([hubs[from].categoryId, hubs[to].categoryId]);
    connected.add(to);
  }

  return links;
}
