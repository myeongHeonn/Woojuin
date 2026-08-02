import { apiFetch } from '@/api/client';

export type ItemStatus = 'PROCESSING' | 'DONE' | 'PARTIAL' | 'FAILED';

export interface ItemCreateResponse {
  itemId: number;
  status: ItemStatus;
  createdAt: string;
}

/** 처리가 끝났는지 — PROCESSING 이 아니면 더 기다릴 게 없다. */
export const isTerminal = (status: ItemStatus) => status !== 'PROCESSING';

/**
 * 아이템 처리 상태. 저장 API 는 접수만 하고 PROCESSING 으로 돌아오므로(AI 처리는 워커가 한다),
 * 실제 완료를 알려면 이걸 확인해야 한다.
 */
export function getItemStatus(itemId: number): Promise<{ itemId: number; status: ItemStatus }> {
  return apiFetch(`/items/${itemId}/status`);
}

export function saveUrl(workspaceId: number, url: string): Promise<ItemCreateResponse> {
  return apiFetch(`/workspaces/${workspaceId}/items`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ type: 'URL', url }),
  });
}

export function saveMemo(workspaceId: number, content: string): Promise<ItemCreateResponse> {
  return apiFetch(`/workspaces/${workspaceId}/items`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ type: 'MEMO', content }),
  });
}

export function saveImage(workspaceId: number, file: File): Promise<ItemCreateResponse> {
  const formData = new FormData();
  formData.append('file', file);
  return apiFetch(`/workspaces/${workspaceId}/items`, { method: 'POST', body: formData });
}
