import { api, type ApiResponse } from './client';
import type { Category } from '@/types/category';
import type { CategoryChip } from '@/components/domain/library/CategoryChipBar';

export interface Workspace {
  id: number;
  name: string;
  type: 'PERSONAL' | 'TEAM';
  role: 'OWNER' | 'MEMBER';
}

export interface CreateWorkspacePayload {
  name: string;
  type: 'PERSONAL' | 'TEAM';
}

export async function fetchMyWorkspaces() {
  const res = await api.get<ApiResponse<Workspace[]>>('/workspaces');
  return res.data.data;
}

export async function createWorkspace(payload: CreateWorkspacePayload) {
  const res = await api.post<ApiResponse<Workspace>>('/workspaces', payload);
  return res.data.data;
}

//워크스페이스 내부 카테고리 목록 가져오기
export async function getCategories(workspaceId: number) {
  const res = await api.get<ApiResponse<Category[]>>(`/workspaces/${workspaceId}/categories`);
  return res.data.data;
}

// 카테고리 생성 — 색은 서버가 배정하므로 이름만 보낸다
export async function createCategory(workspaceId: number, name: string) {
  const res = await api.post<ApiResponse<CategoryChip>>(`/workspaces/${workspaceId}/categories`, {
    name,
  });
  return res.data.data;
}

// 카테고리 이름 변경
export async function renameCategory(workspaceId: number, categoryId: number, name: string) {
  const res = await api.patch<ApiResponse<CategoryChip>>(
    `/workspaces/${workspaceId}/categories/${categoryId}`,
    { name },
  );
  return res.data.data;
}

// 카테고리 삭제 — 응답 data 가 없다(void)
export async function deleteCategory(workspaceId: number, categoryId: number) {
  await api.delete<ApiResponse<null>>(`/workspaces/${workspaceId}/categories/${categoryId}`);
}
