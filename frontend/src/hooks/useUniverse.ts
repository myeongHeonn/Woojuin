import { useQuery } from '@tanstack/react-query';
import { fetchUniverse } from '@/services/universe';

export const universeKey = (workspaceId: number) => ['universe', workspaceId] as const;

/** 워크스페이스의 우주 좌표를 조회한다. 처리 중인 아이템이 있을 때만 호출부가 폴링 간격을 넘긴다. */
export function useUniverse(workspaceId: number, refetchInterval: number | false = false) {
  return useQuery({
    queryKey: universeKey(workspaceId),
    queryFn: () => fetchUniverse(workspaceId),
    enabled: Number.isInteger(workspaceId) && workspaceId > 0,
    refetchInterval,
  });
}
