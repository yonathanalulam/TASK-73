import {
  createRouter,
  createWebHistory,
  type NavigationGuardNext,
  type RouteLocationNormalized,
  type RouteRecordRaw,
} from 'vue-router';
import { useAuthStore } from '@/stores/auth';
import type { UserRoleType } from '@/types/api';

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean;
    roles?: UserRoleType[];
    title?: string;
  }
}

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/pages/LoginPage.vue'),
    meta: { title: 'Sign in' },
  },
  {
    path: '/',
    component: () => import('@/components/AppShell.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        name: 'dashboard',
        component: () => import('@/pages/DashboardPage.vue'),
        meta: { title: 'Dashboard', requiresAuth: true },
      },
      {
        path: 'profile',
        name: 'my-profile',
        component: () => import('@/pages/MyProfilePage.vue'),
        meta: { title: 'My Profile', requiresAuth: true },
      },
      {
        path: 'students',
        name: 'students',
        component: () => import('@/pages/StudentsPage.vue'),
        meta: { title: 'Students', requiresAuth: true, roles: ['STAFF', 'ADMIN'] },
      },
      {
        path: 'training',
        name: 'training',
        component: () => import('@/pages/TrainingPage.vue'),
        meta: { title: 'Training', requiresAuth: true },
      },
      {
        path: 'property',
        name: 'property',
        component: () => import('@/pages/PropertyPage.vue'),
        meta: { title: 'Property', requiresAuth: true },
      },
      {
        path: 'community',
        name: 'community',
        component: () => import('@/pages/CommunityPage.vue'),
        meta: { title: 'Community', requiresAuth: true },
      },
      {
        path: 'notifications',
        name: 'notifications',
        component: () => import('@/pages/NotificationsPage.vue'),
        meta: { title: 'Notifications', requiresAuth: true },
      },
      {
        path: 'moderation',
        name: 'moderation',
        component: () => import('@/pages/ModerationPage.vue'),
        meta: { title: 'Moderation', requiresAuth: true, roles: ['STAFF', 'ADMIN'] },
      },
      {
        path: 'admin/users',
        name: 'admin-users',
        component: () => import('@/pages/AdminUsersPage.vue'),
        meta: { title: 'Users', requiresAuth: true, roles: ['STAFF', 'ADMIN'] },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/pages/NotFoundPage.vue'),
  },
];

export const router = createRouter({
  history: createWebHistory(),
  routes,
});

router.beforeEach(async (
  to: RouteLocationNormalized,
  _from: RouteLocationNormalized,
  next: NavigationGuardNext,
) => {
  const auth = useAuthStore();
  if (!auth.initialized) {
    await auth.refresh();
  }

  const requiresAuth = to.matched.some((r) => r.meta.requiresAuth);
  if (requiresAuth && !auth.isAuthenticated) {
    return next({ name: 'login', query: { redirect: to.fullPath } });
  }
  if (to.name === 'login' && auth.isAuthenticated) {
    return next({ name: 'dashboard' });
  }

  // Role-based gating: if a route declares allowed roles, the current user's
  // primary role must be in that list. This is a UX guard — the backend remains
  // the source of truth via @PreAuthorize / SecurityConfig.
  const allowedRoles = to.meta.roles;
  if (allowedRoles && allowedRoles.length > 0 && auth.primaryRole) {
    if (!allowedRoles.includes(auth.primaryRole)) {
      return next({ name: 'dashboard' });
    }
  }
  return next();
});
