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
