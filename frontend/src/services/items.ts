import { api, type ApiResponse } from './client';
import type { ItemCreateResponse, ItemListResponse } from '@/types/item';

export async function fetchItems(workspaceId: number) {
  const res = await api.get<ApiResponse<ItemListResponse>>(`/workspaces/${workspaceId}/items`);
  return res.data.data;
}

export async function saveUrl(workspaceId: number, url: string) {
  // 즉시 200 응답 (status: PROCESSING) — "저장은 1초" (NFR-001)
  const res = await api.post<ApiResponse<ItemCreateResponse>>(`/workspaces/${workspaceId}/items`, {
    type: 'URL',
    url,
  });
  return res.data.data;
}
