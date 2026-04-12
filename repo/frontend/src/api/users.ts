import { http } from './client';
import type { AdminUser, PageResponse, UserRoleType } from '@/types/api';

export interface ListUsersParams {
  page?: number;
  size?: number;
}

export interface CreateUserPayload {
  username: string;
  fullName: string;
  email?: string;
  primaryRole: UserRoleType;
  organizationId?: number | null;
  password: string;
  roleCodes?: string[];
}

export interface UpdateUserPayload {
  fullName?: string;
  email?: string;
  organizationId?: number | null;
  enabled?: boolean;
}

/**
 * Typed client for /api/users. Every call goes through the shared Axios instance
 * so CSRF, session cookies, and the envelope unwrap all happen centrally.
 */
export const usersApi = {
  async list(params: ListUsersParams = {}): Promise<PageResponse<AdminUser>> {
    const res = await http.get<PageResponse<AdminUser>>('/api/users', { params });
    return res.data;
  },

  async get(id: number): Promise<AdminUser> {
    const res = await http.get<AdminUser>(`/api/users/${id}`);
    return res.data;
  },

  async create(payload: CreateUserPayload): Promise<AdminUser> {
    const res = await http.post<AdminUser>('/api/users', payload);
    return res.data;
  },

  async update(id: number, payload: UpdateUserPayload): Promise<AdminUser> {
    const res = await http.put<AdminUser>(`/api/users/${id}`, payload);
    return res.data;
  },

  async assignRoles(id: number, roleCodes: string[]): Promise<AdminUser> {
    const res = await http.put<AdminUser>(`/api/users/${id}/roles`, { roleCodes });
    return res.data;
  },
};
