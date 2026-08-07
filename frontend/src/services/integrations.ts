import { api, type ApiResponse } from '@/services/client';

export interface ChatLinkCode {
  code: string;
  expiresAt: string;
}

export async function issueChatLinkCode() {
  const response = await api.post<ApiResponse<ChatLinkCode>>('/integrations/chat/link-code');
  return response.data.data;
}

export interface MattermostOAuthAuthorize {
  authorizationUrl: string;
}

export async function authorizeMattermost() {
  const response = await api.post<ApiResponse<MattermostOAuthAuthorize>>(
    '/integrations/mattermost/oauth/authorize',
  );
  return response.data.data;
}

export type ChatPlatform = 'DISCORD' | 'MATTERMOST';

/**
 * 내가 연결한 채팅 계정 하나 — 마이페이지 "연결된 앱" 행 (S15P11C105-461/-460).
 * 계정 이름이 없는 이유: 서버가 표시 이름을 저장하지 않기로 결정했다(-461) —
 * 플랫폼·연결 시각·기본 워크스페이스만으로 간다.
 */
export interface ChatConnection {
  id: number;
  platform: ChatPlatform;
  connectedAt: string;
  defaultWorkspaceId: number | null;
  defaultWorkspaceName: string | null;
}

export async function fetchChatConnections() {
  const response = await api.get<ApiResponse<ChatConnection[]>>('/integrations/connections');
  return response.data.data;
}

/** 연동 해제 — 그 플랫폼에서 더 이상 저장되지 않는다. 즉시 적용된다. */
export async function disconnectChatConnection(connectionId: number) {
  await api.delete<ApiResponse<null>>(`/integrations/connections/${connectionId}`);
}
