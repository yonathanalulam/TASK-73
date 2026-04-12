import { http } from './client';

export interface Notification {
  id: number;
  recipientUserId: number;
  organizationId: number | null;
  kind: string;
  title: string;
  body: string;
  referenceType: string | null;
  referenceId: string | null;
  readAt: string | null;
  createdAt: string;
}

export interface UnreadCountResponse {
  unread: number;
}

export const notificationsApi = {
  async list(): Promise<Notification[]> {
    const res = await http.get<Notification[]>('/api/notifications');
    return res.data;
  },
  async unreadCount(): Promise<UnreadCountResponse> {
    const res = await http.get<UnreadCountResponse>('/api/notifications/unread-count');
    return res.data;
  },
  async markRead(id: number): Promise<Notification> {
    const res = await http.post<Notification>(`/api/notifications/${id}/read`);
    return res.data;
  },
};
