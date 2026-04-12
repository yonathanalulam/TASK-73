<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { moderationApi, type ModerationReport } from '@/api/moderation';
import { ApiException } from '@/api/client';
import { useToast } from '@/composables/useToast';

const toast = useToast();
const loading = ref(false);
const reports = ref<ModerationReport[]>([]);

async function load() {
  loading.value = true;
  try {
    reports.value = await moderationApi.listOpen();
  } catch (err) {
    toast.error(err instanceof ApiException ? err.apiError.message : 'Failed to load reports');
  } finally {
    loading.value = false;
  }
}

async function resolveReport(r: ModerationReport, resolution: 'UPHELD' | 'DISMISSED') {
  try {
    const updated = await moderationApi.resolve(r.id, {
      resolution,
      hideTarget: resolution === 'UPHELD',
      resolutionNotes: resolution === 'UPHELD' ? 'Upheld by reviewer' : 'Dismissed by reviewer',
    });
    reports.value = reports.value.filter(x => x.id !== updated.id);
    toast.success(`Report ${resolution.toLowerCase()}`);
  } catch (err) {
    toast.error(err instanceof ApiException ? err.apiError.message : 'Action failed');
  }
}

async function restore(r: ModerationReport) {
  try {
    await moderationApi.restore(r.targetType, r.targetId);
    toast.success(`${r.targetType} restored`);
  } catch (err) {
    toast.error(err instanceof ApiException ? err.apiError.message : 'Restore failed');
  }
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString();
}

onMounted(load);
</script>

<template>
  <section class="moderation">
    <header>
      <h1>Moderation Queue</h1>
      <p class="muted">Open reports within your data scope. Upheld reports hide the target content; dismissed reports close without action.</p>
    </header>

    <div v-if="loading && reports.length === 0" class="placeholder">Loading...</div>

    <div v-else-if="reports.length > 0" class="list">
      <div v-for="r in reports" :key="r.id" class="report-card">
        <div class="report-header">
          <span class="target">{{ r.targetType }} #{{ r.targetId }}</span>
          <span class="time">{{ formatDate(r.createdAt) }}</span>
        </div>
        <div class="reason"><strong>Reason:</strong> {{ r.reason }}</div>
        <div v-if="r.details" class="details">{{ r.details }}</div>
        <div class="report-meta">Reporter: User #{{ r.reporterUserId }} &middot; Org #{{ r.organizationId }}</div>
        <div class="report-actions">
          <button type="button" class="btn btn-danger" @click="resolveReport(r, 'UPHELD')">Uphold &amp; Hide</button>
          <button type="button" class="btn btn-muted" @click="resolveReport(r, 'DISMISSED')">Dismiss</button>
          <button type="button" class="btn btn-link" @click="restore(r)">Restore Target</button>
        </div>
      </div>
    </div>

    <div v-else class="placeholder">No open reports. The queue is clear.</div>
  </section>
</template>

<style scoped>
.moderation h1 { margin: 0 0 4px; }
.muted { color: var(--color-text-muted); margin: 0 0 16px; }
.list { display: flex; flex-direction: column; gap: 12px; }
.report-card { background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius); padding: 16px; }
.report-header { display: flex; justify-content: space-between; margin-bottom: 8px; }
.target { font-weight: 600; font-size: 14px; }
.time { font-size: 11px; color: var(--color-text-muted); }
.reason { font-size: 13px; margin-bottom: 4px; }
.details { font-size: 13px; color: var(--color-text-muted); margin-bottom: 4px; }
.report-meta { font-size: 11px; color: var(--color-text-muted); margin-bottom: 8px; }
.report-actions { display: flex; gap: 8px; }
.btn { padding: 6px 14px; border-radius: var(--radius); border: 1px solid var(--color-border); cursor: pointer; font-size: 13px; font-weight: 500; }
.btn-danger { background: #d93025; color: #fff; border-color: #d93025; }
.btn-muted { background: var(--color-bg); }
.btn-link { background: none; border: none; color: var(--color-primary); }
.placeholder { padding: 24px; text-align: center; color: var(--color-text-muted); background: var(--color-surface); border: 1px dashed var(--color-border); border-radius: var(--radius); }
</style>
