import { apiFetch } from '@/api/client';

export interface Workspace {
  id: number;
  name: string;
  type: 'PERSONAL' | 'TEAM';
  role: 'OWNER' | 'MEMBER';
}

export function getWorkspaces(): Promise<Workspace[]> {
  return apiFetch<Workspace[]>('/workspaces');
}
