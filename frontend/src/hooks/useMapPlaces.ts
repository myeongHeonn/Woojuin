import { useQuery } from '@tanstack/react-query';
import { fetchMapPlaces } from '@/services/items';

export function useMapPlaces(workspaceId: number) {
  return useQuery({
    queryKey: ['map-places', workspaceId],
    queryFn: () => fetchMapPlaces(workspaceId),
    enabled: Number.isFinite(workspaceId),
  });
}
