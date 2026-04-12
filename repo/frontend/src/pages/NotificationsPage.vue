<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { notificationsApi, type Notification } from '@/api/notifications';
import { ApiException } from '@/api/client';
import { useToast } from '@/composables/useToast';

const toast = useToast();
const loading = ref(false);
const notifications = ref<Notification[]>([]);

async function load() {
  loading.value = true;
  try {
    notifications.value = await notificationsApi.list();
  } catch (err) {
    toast.error(err instanceof ApiException ? err.apiError.message : 'Failed to load notifications');
  } finally {
    loading.value = false;
  }
}

async function markRead(n: Notification) {
  if (n.readAt) return;
  try {
    const updated = await notificationsApi.markRead(n.id);
    notifications.value = notifications.value.map(x => x.id === updated.id ? updated : x);
  } catch {
    toast.error('Failed to mark as read');
  }
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString();
}

onMounted(load);
</script>

<template>
  <section class="notifications">
    <header>
      <h1>Notifications</h1>
    </header>

    <div v-if="loading && notifications.length === 0" class="placeholder">Loading...</div>

    <div v-else-if="notifications.length > 0" class="list">
      <div v-for="n in notifications" :key="n.id"
           :class="['notif', { unread: !n.readAt }]"
           @click="markRead(n)">
        <div class="notif-header">
          <span class="kind">{{ n.kind }}</span>
          <span class="time">{{ formatDate(n.createdAt) }}</span>
        </div>
        <h3>{{ n.title }}</h3>
        <p>{{ n.body }}</p>
        <span v-if="!n.readAt" class="unread-dot" />
      </div>
    </div>

    <div v-else class="placeholder">No notifications.</div>
  </section>
</template>

<style scoped>
.notifications h1 { margin: 0 0 16px; }
.list { display: flex; flex-direction: column; gap: 8px; }
.notif { position: relative; background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius); padding: 12px 16px; cursor: pointer; transition: background 0.15s; }
.notif:hover { background: var(--color-bg); }
.notif.unread { border-left: 3px solid var(--color-primary); }
.notif-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
.kind { font-size: 11px; font-weight: 600; text-transform: uppercase; color: var(--color-primary); }
.time { font-size: 11px; color: var(--color-text-muted); }
.notif h3 { margin: 0 0 2px; font-size: 14px; }
.notif p { margin: 0; font-size: 13px; color: var(--color-text-muted); }
.unread-dot { position: absolute; top: 12px; right: 12px; width: 8px; height: 8px; background: var(--color-primary); border-radius: 50%; }
.placeholder { padding: 24px; text-align: center; color: var(--color-text-muted); background: var(--color-surface); border: 1px dashed var(--color-border); border-radius: var(--radius); }
</style>
