import { apiFetch } from '@/api/client';

export interface ItemCreateResponse {
  itemId: number;
  status: 'PROCESSING' | 'DONE' | 'PARTIAL' | 'FAILED';
  createdAt: string;
}

export function saveUrl(workspaceId: number, url: string): Promise<ItemCreateResponse> {
  return apiFetch(`/workspaces/${workspaceId}/items`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ type: 'URL', url }),
  });
}

export function saveMemo(workspaceId: number, content: string, title?: string): Promise<ItemCreateResponse> {
  return apiFetch(`/workspaces/${workspaceId}/items`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ type: 'MEMO', content, title }),
  });
}

export function saveImage(workspaceId: number, file: File, title?: string): Promise<ItemCreateResponse> {
  const formData = new FormData();
  formData.append('file', file);
  if (title) formData.append('title', title);
  return apiFetch(`/workspaces/${workspaceId}/items`, { method: 'POST', body: formData });
}
