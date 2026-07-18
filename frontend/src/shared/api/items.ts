import { api, type ApiResponse } from './client';

/** 저장 항목 처리 상태 (FR-025) */
export type ItemStatus = 'PROCESSING' | 'DONE' | 'PARTIAL' | 'FAILED';

export interface Item {
  id: number;
  type: 'URL' | 'IMAGE' | 'MEMO';
  status: ItemStatus;
  title: string | null;
  summary: string | null;
  tags: string[];
  createdAt: string;
}

export async function fetchItems(workspaceId: number) {
  const res = await api.get<ApiResponse<Item[]>>(`/workspaces/${workspaceId}/items`);
  return res.data.data;
}

export async function saveUrl(workspaceId: number, url: string) {
  // 즉시 200 응답 (status: PROCESSING) — "저장은 1초" (NFR-001)
  const res = await api.post<ApiResponse<Item>>(`/workspaces/${workspaceId}/items`, {
    type: 'URL',
    url,
  });
  return res.data.data;
}
