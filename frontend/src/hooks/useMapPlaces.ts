import { useQuery } from '@tanstack/react-query';
import { fetchMapPlaces } from '@/services/items';

/**
 * 워크스페이스의 지도 장소 — 위치가 추출된 아이템만 온다(/items/geo).
 *
 * refetchInterval: 처리 중인 아이템이 있는 동안만 폴링해서, 위치 추출이 끝나면 핀이
 * 자동으로 뜨게 한다. "처리 중인가"는 이 응답(장소)엔 없는 정보라(status·미처리 항목이
 * 빠져 있다) 호출부(MapPage)가 items 상태에서 구해 넘긴다. 없으면 폴링하지 않는다.
 */
export function useMapPlaces(workspaceId: number, refetchInterval: number | false = false) {
  return useQuery({
    queryKey: ['map-places', workspaceId],
    queryFn: () => fetchMapPlaces(workspaceId),
    enabled: Number.isFinite(workspaceId),
    refetchInterval,
  });
}
