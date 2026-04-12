<script setup lang="ts">
import { onMounted, ref, reactive } from 'vue';
import { scopesApi, type DataScopeRule, type ReplaceScopeRulesRequest } from '@/api/scopes';
import { ApiException } from '@/api/client';
import { useToast } from '@/composables/useToast';

const toast = useToast();
const loading = ref(false);
const allRules = ref<DataScopeRule[]>([]);

// Edit state
const editUserId = ref<number | null>(null);
const userRules = ref<DataScopeRule[]>([]);
const newRules = reactive<{ scopeType: string; scopeTargetId: number }[]>([]);
const saving = ref(false);

// Lookup
const lookupUserId = ref<number>(0);

async function loadAll() {
  loading.value = true;
  try {
    allRules.value = await scopesApi.listAll();
  } catch (err) {
    toast.error(err instanceof ApiException ? err.apiError.message : 'Failed to load scope rules');
  } finally {
    loading.value = false;
  }
}

async function lookupUser() {
  if (!lookupUserId.value) return;
  editUserId.value = lookupUserId.value;
  try {
    userRules.value = await scopesApi.listForUser(lookupUserId.value);
    newRules.length = 0;
    for (const r of userRules.value) {
      newRules.push({ scopeType: r.scopeType, scopeTargetId: r.scopeTargetId });
    }
  } catch (err) {
    toast.error('Failed to load rules for user');
  }
}

function addRule() {
  newRules.push({ scopeType: 'ORGANIZATION', scopeTargetId: 0 });
}

function removeRule(index: number) {
  newRules.splice(index, 1);
}

async function saveRules() {
  if (editUserId.value == null) return;
  saving.value = true;
  try {
    const req: ReplaceScopeRulesRequest = { rules: [...newRules] };
    userRules.value = await scopesApi.replaceForUser(editUserId.value, req);
    toast.success('Scope rules saved');
    loadAll();
  } catch (err) {
    toast.error(err instanceof ApiException ? err.apiError.message : 'Failed to save rules');
  } finally {
    saving.value = false;
  }
}

onMounted(loadAll);
</script>

<template>
  <section class="scopes">
    <header>
      <h1>Data Scope Management</h1>
      <p class="muted">Manage data scope rules that control which organizations, departments, and facilities each user can access.</p>
    </header>

    <!-- Lookup & Edit -->
    <div class="edit-panel">
      <h3>Manage User Scope</h3>
      <div class="lookup-form">
        <label>
          User ID:
          <input v-model.number="lookupUserId" type="number" min="1" />
        </label>
        <button type="button" @click="lookupUser">Load Rules</button>
      </div>

      <div v-if="editUserId !== null" class="rule-editor">
        <h4>Rules for User #{{ editUserId }}</h4>
        <div v-for="(rule, i) in newRules" :key="i" class="rule-row">
          <select v-model="rule.scopeType">
            <option value="ORGANIZATION">Organization</option>
            <option value="DEPARTMENT">Department</option>
            <option value="FACILITY_AREA">Facility Area</option>
          </select>
          <label>
            Target ID:
            <input v-model.number="rule.scopeTargetId" type="number" min="1" />
          </label>
          <button type="button" class="btn-danger" @click="removeRule(i)">Remove</button>
        </div>
        <div class="rule-actions">
          <button type="button" @click="addRule">+ Add Rule</button>
          <button type="button" :disabled="saving" @click="saveRules">
            {{ saving ? 'Saving...' : 'Save Rules' }}
          </button>
        </div>
      </div>
    </div>

    <!-- All rules overview -->
    <div class="overview">
      <h3>All Scope Assignments</h3>
      <div v-if="loading" class="placeholder">Loading...</div>
      <div v-else-if="allRules.length === 0" class="placeholder">No scope rules configured.</div>
      <table v-else class="table">
        <thead>
          <tr><th>User ID</th><th>Scope Type</th><th>Target ID</th><th>Created</th></tr>
        </thead>
        <tbody>
          <tr v-for="r in allRules" :key="r.id">
            <td>{{ r.userId }}</td>
            <td>{{ r.scopeType }}</td>
            <td>{{ r.scopeTargetId }}</td>
            <td>{{ new Date(r.createdAt).toLocaleString() }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<style scoped>
.scopes h1 { margin: 0 0 4px; }
.muted { color: var(--color-text-muted); margin: 0 0 16px; }
.edit-panel { background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius); padding: 16px; margin-bottom: 24px; }
.edit-panel h3 { margin: 0 0 12px; }
.lookup-form { display: flex; align-items: center; gap: 8px; margin-bottom: 16px; }
.lookup-form label { display: flex; align-items: center; gap: 6px; font-size: 13px; }
.lookup-form input { padding: 4px 8px; border: 1px solid var(--color-border); border-radius: var(--radius); width: 100px; }
.lookup-form button { padding: 6px 14px; border: 1px solid var(--color-border); border-radius: var(--radius); background: var(--color-primary); color: #fff; cursor: pointer; }
.rule-editor h4 { margin: 0 0 8px; }
.rule-row { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.rule-row select, .rule-row input { padding: 4px 8px; border: 1px solid var(--color-border); border-radius: var(--radius); font-size: 13px; }
.rule-row label { display: flex; align-items: center; gap: 4px; font-size: 13px; }
.rule-actions { display: flex; gap: 8px; margin-top: 12px; }
.rule-actions button { padding: 6px 14px; border: 1px solid var(--color-border); border-radius: var(--radius); cursor: pointer; }
.rule-actions button:last-child { background: var(--color-primary); color: #fff; border-color: var(--color-primary); }
.rule-actions button:disabled { opacity: 0.5; }
.btn-danger { color: #a0282a; border: 1px solid #a0282a; background: none; border-radius: var(--radius); cursor: pointer; padding: 3px 10px; font-size: 12px; }
.overview { margin-top: 16px; }
.overview h3 { margin: 0 0 12px; }
.table { width: 100%; border-collapse: collapse; background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius); overflow: hidden; }
.table th, .table td { text-align: left; padding: 8px 10px; border-bottom: 1px solid var(--color-border); font-size: 13px; }
.table thead th { background: var(--color-bg); font-weight: 600; }
.placeholder { padding: 24px; text-align: center; color: var(--color-text-muted); background: var(--color-surface); border: 1px dashed var(--color-border); border-radius: var(--radius); }
</style>
