import { api, type ApiResponse } from './client';
import type { ItemCreateResponse, ItemListResponse } from '@/types/item';

//워크스페이스 아이템 받아오기
//이후 선호도를 추가해야하므로 interface로 만들어둔다.
export interface workspaceProps {
  size: number;
  page?: number;
  workspaceId: number;
}

export async function fetchItems({ size, page, workspaceId }: workspaceProps) {
  const res = await api.get<ApiResponse<ItemListResponse>>(
    `/workspaces/${workspaceId}/items?page=${page}&size=${size}`,
  );
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

export async function saveMemo(workspaceId: number, content: string) {
  // 즉시 200 응답 (status: PROCESSING) — "저장은 1초" (NFR-001)
  const res = await api.post<ApiResponse<ItemCreateResponse>>(`/workspaces/${workspaceId}/items`, {
    type: 'MEMO',
    content: content,
  });
  return res.data.data;
}

export async function saveImage(workspaceId: number, file: File) {
  // 이미지만 multipart/form-data 다. 일반 객체로 넘기면 axios 가 JSON 으로 보내 400 이 난다.
  // Content-Type 은 직접 지정하지 않는다 — boundary 가 빠져서 파싱이 깨진다 (브라우저가 붙여준다).
  const formData = new FormData();
  formData.append('file', file);

  // 즉시 200 응답 (status: PROCESSING) — "저장은 1초" (NFR-001)
  const res = await api.post<ApiResponse<ItemCreateResponse>>(
    `/workspaces/${workspaceId}/items`,
    formData,
  );
  return res.data.data;
}
