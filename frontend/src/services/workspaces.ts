import { api, type ApiResponse } from './client';

export interface Workspace {
  id: number;
  name: string;
  type: 'PERSONAL' | 'TEAM';
  role: 'OWNER' | 'MEMBER';
}

export async function fetchMyWorkspaces() {
  const res = await api.get<ApiResponse<Workspace[]>>('/workspaces');
  return res.data.data;
}
