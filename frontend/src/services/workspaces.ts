import { api, type ApiResponse } from './client';
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

export interface WorkspaceMember {
  userId: number;
  nickname: string;
  email: string;
  role: 'OWNER' | 'MEMBER';
  joinedAt: string;
}

/** 초대 응답 — code 로 공유 링크를 만든다 */
export interface Invitation {
  code: string;
  workspaceId: number;
  workspaceName: string;
  expiresAt: string;
}

export async function fetchMyWorkspaces() {
  const res = await api.get<ApiResponse<Workspace[]>>('/workspaces');
  return res.data.data;
}

export async function createWorkspace(payload: CreateWorkspacePayload) {
  const res = await api.post<ApiResponse<Workspace>>('/workspaces', payload);
  return res.data.data;
}

// ── 공유(멤버·초대) ─────────────────────────────

// 멤버 목록
export async function fetchMembers(workspaceId: number) {
  const res = await api.get<ApiResponse<WorkspaceMember[]>>(`/workspaces/${workspaceId}/members`);
  return res.data.data;
}

// 초대 생성 — code 를 받아 공유 링크로 쓴다
export async function createInvitation(workspaceId: number) {
  const res = await api.post<ApiResponse<Invitation>>(`/workspaces/${workspaceId}/invitations`);
  return res.data.data;
}

// 멤버 제거 — 본인 userId 면 탈퇴, 다른 사람이면 OWNER 의 강퇴(서버가 구분)
export async function removeMember(workspaceId: number, userId: number) {
  await api.delete<ApiResponse<null>>(`/workspaces/${workspaceId}/members/${userId}`);
}

// 초대 정보 조회 — 링크(코드)로 워크스페이스 이름 등을 미리 본다(로그인 전에도)
export async function fetchInvitation(code: string) {
  const res = await api.get<ApiResponse<Invitation>>(`/invitations/${code}`);
  return res.data.data;
}

// 초대 수락 — 참여한 워크스페이스를 돌려준다
export async function acceptInvitation(code: string) {
  const res = await api.post<ApiResponse<Workspace>>(`/invitations/${code}/accept`);
  return res.data.data;
}

//워크스페이스 내부 카테고리 목록 가져오기
export async function getCategories(workspaceId: number) {
  const res = await api.get<ApiResponse<CategoryChip[]>>(`/workspaces/${workspaceId}/categories`);
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
