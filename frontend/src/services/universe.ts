import { api, type ApiResponse } from '@/services/client';
import type { ItemType } from '@/types/item';
import { UNCLASSIFIED_COLOR, type Star, type UniverseResponse, type Vec3 } from '@/types/universe';

interface UniverseApiStar {
  id: number;
  position: number[];
  title: string | null;
  type: ItemType;
}

interface UniverseApiConstellation {
  categoryId: number;
  categoryName: string;
  color: string;
  items: UniverseApiStar[];
}

interface UniverseApiResponse {
  constellations: UniverseApiConstellation[];
  unclassified: UniverseApiStar[];
}

function toPosition(position: number[]): Vec3 {
  if (position.length !== 3 || position.some((coordinate) => !Number.isFinite(coordinate))) {
    throw new Error('우주 좌표 응답이 올바르지 않습니다.');
  }
  return [position[0], position[1], position[2]];
}

function toColor(color: string): number {
  const hex = color.replace(/^#/, '');
  return /^[0-9a-f]{6}$/i.test(hex) ? Number.parseInt(hex, 16) : UNCLASSIFIED_COLOR;
}

function toStar(star: UniverseApiStar): Star {
  return {
    id: star.id,
    position: toPosition(star.position),
    title: star.title?.trim() || '제목 없음',
    type: star.type,
  };
}

/** 우주 뷰의 별자리와 3D 좌표를 조회하고 렌더러가 쓰는 형태로 정규화한다. */
export async function fetchUniverse(workspaceId: number): Promise<UniverseResponse> {
  const response = await api.get<ApiResponse<UniverseApiResponse>>(
    `/workspaces/${workspaceId}/universe`,
  );
  const { constellations, unclassified } = response.data.data;

  return {
    constellations: constellations.map((constellation) => ({
      categoryId: constellation.categoryId,
      categoryName: constellation.categoryName,
      color: toColor(constellation.color),
      items: constellation.items.map(toStar),
    })),
    unclassified: unclassified.map(toStar),
  };
}
