import type { ItemType } from '@/types/item';

export type Vec3 = [number, number, number];

/** 미분류 별은 별자리에 속하지 않으므로 중립색으로 표시한다. */
export const UNCLASSIFIED_COLOR = 0xf5f1e8;

/** 아이템 별의 기본 크기. 별자리 중심 크기는 소속 아이템 수로 계산한다. */
export const STAR_RADIUS = 0.6;

export interface Star {
  id: number;
  position: Vec3;
  title: string;
  type: ItemType;
  /**
   * 우주 API에는 원본 URL이 없지만, 렌더러를 재사용할 때 URL이 주어지면
   * 상세 모달 대신 새 탭으로 열 수 있다.
   */
  url?: string;
}

export interface Constellation {
  categoryId: number;
  categoryName: string;
  /** Three.js가 바로 사용할 수 있도록 서버의 #RRGGBB를 숫자로 정규화한 값 */
  color: number;
  items: Star[];
}

export interface UniverseResponse {
  constellations: Constellation[];
  unclassified: Star[];
}
