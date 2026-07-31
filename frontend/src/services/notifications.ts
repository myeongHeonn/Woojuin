import { api, type ApiResponse } from './client';

export interface NotificationItem {
  id: number;
  itemId: number;
  message: string;
  createdAt: string;
}

export async function registerNotificationToken(token: string, deviceInfo: string) {
  await api.post<ApiResponse<null>>('/notifications/tokens', { token, deviceInfo });
}

export async function deleteNotificationToken(token: string) {
  await api.delete<ApiResponse<null>>('/notifications/tokens', { data: { token } });
}

export async function fetchNotifications() {
  const res = await api.get<ApiResponse<NotificationItem[]>>('/notifications');
  return res.data.data;
}
