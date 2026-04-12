<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { usersApi } from '@/api/users';
import type { AdminUser, PageResponse } from '@/types/api';
import { ApiException } from '@/api/client';
import { useToast } from '@/composables/useToast';

/**
 * Minimal Phase 2 admin users page. Lists the users the current caller can see
 * according to their data scope (admins see everyone, scoped staff see only their
 * organization), with a lock/unlock convenience action routed through the existing
 * auth unlock endpoint. Create/edit forms land in a later phase.
 */
const toast = useToast();

const loading = ref(false);
const page = ref<PageResponse<AdminUser> | null>(null);
const pageNumber = ref(0);
const pageSize = ref(20);

async function load() {
  loading.value = true;
  try {
    page.value = await usersApi.list({ page: pageNumber.value, size: pageSize.value });
  } catch (err) {
    const msg = err instanceof ApiException ? err.apiError.message : 'Failed to load users';
    toast.error(msg);
  } finally {
    loading.value = false;
  }
}

async function toggleEnabled(user: AdminUser) {
  try {
    const updated = await usersApi.update(user.id, { enabled: !user.enabled });
    // Patch the row in-place so the page doesn't scroll/reset.
    if (page.value) {
      page.value.content = page.value.content.map((u) => (u.id === updated.id ? updated : u));
    }
    toast.success(updated.enabled ? 'User enabled' : 'User disabled');
  } catch (err) {
    const msg = err instanceof ApiException ? err.apiError.message : 'Update failed';
    toast.error(msg);
  }
}

function prev() {
  if (page.value && !page.value.first) {
    pageNumber.value -= 1;
    load();
  }
}

function next() {
  if (page.value && !page.value.last) {
    pageNumber.value += 1;
    load();
  }
}

onMounted(load);
</script>

<template>
  <section class="users">
    <header>
      <h1>Users</h1>
      <p class="muted">
        Showing users within your data scope. Administrators see every user;
        scoped staff see only users in their assigned organization(s).
      </p>
    </header>

    <div v-if="loading && !page" class="placeholder">Loading…</div>

    <table v-else-if="page && page.content.length > 0" class="table">
      <thead>
        <tr>
          <th>Username</th>
          <th>Name</th>
          <th>Email</th>
          <th>Primary role</th>
          <th>Org</th>
          <th>Roles</th>
          <th>Status</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="u in page.content" :key="u.id">
          <td>{{ u.username }}</td>
          <td>{{ u.fullName }}</td>
          <td>{{ u.email ?? '—' }}</td>
          <td>{{ u.primaryRole }}</td>
          <td>{{ u.organizationId ?? '—' }}</td>
          <td>{{ u.roles.join(', ') || '—' }}</td>
          <td>
            <span :class="['badge', u.enabled ? 'badge-ok' : 'badge-off']">
              {{ u.enabled ? 'Enabled' : 'Disabled' }}
            </span>
          </td>
          <td>
            <button type="button" class="link" @click="toggleEnabled(u)">
              {{ u.enabled ? 'Disable' : 'Enable' }}
            </button>
          </td>
        </tr>
      </tbody>
    </table>

    <div v-else class="placeholder">No users visible within your current data scope.</div>

    <footer v-if="page" class="pager">
      <button type="button" :disabled="page.first || loading" @click="prev">Previous</button>
      <span>Page {{ page.number + 1 }} of {{ Math.max(page.totalPages, 1) }}</span>
      <button type="button" :disabled="page.last || loading" @click="next">Next</button>
    </footer>
  </section>
</template>

<style scoped>
.users h1 {
  margin: 0 0 4px;
}
.muted {
  color: var(--color-text-muted);
  margin: 0 0 16px;
}
.table {
  width: 100%;
  border-collapse: collapse;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  overflow: hidden;
}
.table th,
.table td {
  text-align: left;
  padding: 10px 12px;
  border-bottom: 1px solid var(--color-border);
  font-size: 13px;
}
.table thead th {
  background: var(--color-bg);
  font-weight: 600;
}
.badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
}
.badge-ok {
  background: #e6f6ed;
  color: #157a3c;
}
.badge-off {
  background: #fbeaea;
  color: #a0282a;
}
.link {
  background: none;
  border: none;
  color: var(--color-primary);
  cursor: pointer;
  padding: 0;
  font: inherit;
}
.pager {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 16px;
  font-size: 13px;
}
.placeholder {
  padding: 24px;
  text-align: center;
  color: var(--color-text-muted);
  background: var(--color-surface);
  border: 1px dashed var(--color-border);
  border-radius: var(--radius);
}
</style>
