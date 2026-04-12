<script setup lang="ts">
import { useAuthStore } from '@/stores/auth';
import { useRouter } from 'vue-router';
import AppSidebar from './AppSidebar.vue';
import { useToast } from '@/composables/useToast';

const auth = useAuthStore();
const router = useRouter();
const toast = useToast();

async function handleLogout() {
  try {
    await auth.logout();
    toast.success('Signed out');
    await router.push({ name: 'login' });
  } catch (err) {
    toast.error('Failed to sign out');
    // eslint-disable-next-line no-console
    console.error(err);
  }
}
</script>

<template>
  <div class="shell">
    <header class="topbar">
      <div class="brand">DojoStay</div>
      <div class="user-area" v-if="auth.user">
        <span class="user-name">{{ auth.user.fullName }}</span>
        <span class="user-role">{{ auth.user.primaryRole }}</span>
        <button class="logout-btn" @click="handleLogout">Sign out</button>
      </div>
    </header>
    <div class="body">
      <AppSidebar />
      <main class="content">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<style scoped>
.shell {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}
.topbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 24px;
  background: var(--color-surface);
  border-bottom: 1px solid var(--color-border);
}
.brand {
  font-weight: 700;
  font-size: 18px;
}
.user-area {
  display: flex;
  gap: 12px;
  align-items: center;
}
.user-role {
  font-size: 11px;
  background: var(--color-bg);
  padding: 2px 8px;
  border-radius: 999px;
  border: 1px solid var(--color-border);
  text-transform: uppercase;
}
.body {
  display: flex;
  flex: 1;
  min-height: 0;
}
.content {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
}
.logout-btn {
  font-size: 13px;
}
</style>
