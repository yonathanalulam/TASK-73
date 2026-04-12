import { defineStore } from 'pinia';
import { authApi, type LoginPayload } from '@/api/auth';
import type { CurrentUser, UserRoleType } from '@/types/api';

interface AuthState {
  user: CurrentUser | null;
  loading: boolean;
  initialized: boolean;
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    user: null,
    loading: false,
    initialized: false,
  }),
  getters: {
    isAuthenticated: (state): boolean => state.user !== null,
    primaryRole: (state): UserRoleType | null => state.user?.primaryRole ?? null,
    hasRole: (state) => (code: string): boolean => state.user?.roles?.includes(code) ?? false,
    hasPermission: (state) => (code: string): boolean =>
      state.user?.permissions?.includes(code) ?? false,
  },
  actions: {
    async login(payload: LoginPayload): Promise<void> {
      this.loading = true;
      try {
        this.user = await authApi.login(payload);
      } finally {
        this.loading = false;
      }
    },
    async logout(): Promise<void> {
      try {
        await authApi.logout();
      } finally {
        this.user = null;
      }
    },
    async refresh(): Promise<void> {
      try {
        this.user = await authApi.me();
      } catch {
        this.user = null;
      } finally {
        this.initialized = true;
      }
    },
  },
});
