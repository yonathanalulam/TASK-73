import { http, ensureCsrf } from './client';
import type { CurrentUser } from '@/types/api';

export interface LoginPayload {
  username: string;
  password: string;
}

export const authApi = {
  async login(payload: LoginPayload): Promise<CurrentUser> {
    await ensureCsrf();
    const res = await http.post<CurrentUser>('/api/auth/login', payload);
    return res.data;
  },

  async logout(): Promise<void> {
    await http.post('/api/auth/logout');
  },

  async me(): Promise<CurrentUser> {
    const res = await http.get<CurrentUser>('/api/auth/me');
    return res.data;
  },

  async changePassword(currentPassword: string, newPassword: string): Promise<void> {
    await http.post('/api/auth/change-password', { currentPassword, newPassword });
  },

  async unlock(userId: number): Promise<void> {
    await http.post(`/api/auth/unlock/${userId}`);
  },
};
