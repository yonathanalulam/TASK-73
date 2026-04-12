<script setup lang="ts">
import { computed } from 'vue';
import { useAuthStore } from '@/stores/auth';
import type { UserRoleType } from '@/types/api';

interface NavItem {
  label: string;
  to: string;
  roles: UserRoleType[]; // empty = all
}

// Phase 1 navigation. Each future phase plugs more entries in here, gated on the
// user's primary role. The backend remains the source of truth for actual access.
const NAV: NavItem[] = [
  { label: 'Dashboard', to: '/', roles: [] },
  { label: 'My Profile', to: '/profile', roles: ['STUDENT'] },
  { label: 'Students', to: '/students', roles: ['STAFF', 'ADMIN'] },
  { label: 'Training', to: '/training', roles: [] },
  { label: 'Property', to: '/property', roles: [] },
  { label: 'Community', to: '/community', roles: [] },
  { label: 'Notifications', to: '/notifications', roles: [] },
  { label: 'Moderation', to: '/moderation', roles: ['STAFF', 'ADMIN'] },
  { label: 'Users', to: '/admin/users', roles: ['STAFF', 'ADMIN'] },
];

const auth = useAuthStore();

const visibleNav = computed(() => {
  const role = auth.primaryRole;
  return NAV.filter((n) => n.roles.length === 0 || (role !== null && n.roles.includes(role)));
});
</script>

<template>
  <nav class="sidebar" aria-label="Primary">
    <RouterLink
      v-for="item in visibleNav"
      :key="item.to"
      :to="item.to"
      class="nav-item"
      active-class="nav-item-active"
    >
      {{ item.label }}
    </RouterLink>
  </nav>
</template>

<style scoped>
.sidebar {
  width: 220px;
  background: var(--color-surface);
  border-right: 1px solid var(--color-border);
  padding: 16px 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.nav-item {
  padding: 10px 14px;
  border-radius: var(--radius);
  color: var(--color-text);
  font-weight: 500;
}
.nav-item:hover {
  background: var(--color-bg);
  text-decoration: none;
}
.nav-item-active {
  background: var(--color-primary);
  color: #ffffff;
}
.nav-item-active:hover {
  background: var(--color-primary-hover);
}
</style>
