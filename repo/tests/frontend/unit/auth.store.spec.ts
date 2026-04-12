import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { useAuthStore } from '@/stores/auth';
import { authApi } from '@/api/auth';
import type { CurrentUser } from '@/types/api';

// Use the same module specifiers as the SUT (`@/api/auth`) so vi.mock canonicalizes
// to the same module URL as the store's import.
vi.mock('@/api/auth', () => ({
  authApi: {
    login: vi.fn(),
    logout: vi.fn(),
    me: vi.fn(),
    changePassword: vi.fn(),
    unlock: vi.fn(),
  },
}));

const fakeUser: CurrentUser = {
  id: 1,
  username: 'alice',
  fullName: 'Alice Admin',
  primaryRole: 'ADMIN',
  roles: ['ADMIN'],
  permissions: ['users.read', 'users.write'],
};

describe('auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });

  it('starts unauthenticated', () => {
    const store = useAuthStore();
    expect(store.isAuthenticated).toBe(false);
    expect(store.user).toBeNull();
  });

  it('login populates the user and flips isAuthenticated', async () => {
    (authApi.login as ReturnType<typeof vi.fn>).mockResolvedValue(fakeUser);
    const store = useAuthStore();
    await store.login({ username: 'alice', password: 'CorrectHorse9!' });
    expect(store.user).toEqual(fakeUser);
    expect(store.isAuthenticated).toBe(true);
    expect(store.primaryRole).toBe('ADMIN');
    expect(store.hasRole('ADMIN')).toBe(true);
    expect(store.hasPermission('users.read')).toBe(true);
    expect(store.hasPermission('audit.read')).toBe(false);
  });

  it('logout clears the user even if the API call rejects', async () => {
    (authApi.login as ReturnType<typeof vi.fn>).mockResolvedValue(fakeUser);
    (authApi.logout as ReturnType<typeof vi.fn>).mockRejectedValue(new Error('boom'));
    const store = useAuthStore();
    await store.login({ username: 'alice', password: 'CorrectHorse9!' });
    expect(store.isAuthenticated).toBe(true);
    await store.logout().catch(() => {});
    expect(store.user).toBeNull();
    expect(store.isAuthenticated).toBe(false);
  });

  it('refresh sets initialized=true even when /me 401s', async () => {
    (authApi.me as ReturnType<typeof vi.fn>).mockRejectedValue(new Error('401'));
    const store = useAuthStore();
    await store.refresh();
    expect(store.initialized).toBe(true);
    expect(store.user).toBeNull();
  });

  it('refresh populates user when /me succeeds', async () => {
    (authApi.me as ReturnType<typeof vi.fn>).mockResolvedValue(fakeUser);
    const store = useAuthStore();
    await store.refresh();
    expect(store.user).toEqual(fakeUser);
    expect(store.initialized).toBe(true);
  });
});
