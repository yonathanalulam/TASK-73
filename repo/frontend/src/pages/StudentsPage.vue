<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { studentsApi, type Student, type BulkImportResult } from '@/api/students';
import { ApiException } from '@/api/client';
import { useToast } from '@/composables/useToast';

const toast = useToast();
const loading = ref(false);
const students = ref<Student[]>([]);
const currentPage = ref(0);
const totalPages = ref(0);
const totalElements = ref(0);
const pageSize = 20;

// Import state
const showImport = ref(false);
const importFile = ref<File | null>(null);
const importOrgId = ref<number>(1);
const importing = ref(false);
const importResult = ref<BulkImportResult | null>(null);

async function load(page = 0) {
  loading.value = true;
  try {
    const result = await studentsApi.list(page, pageSize);
    students.value = result.content;
    currentPage.value = result.number;
    totalPages.value = result.totalPages;
    totalElements.value = result.totalElements;
  } catch (err) {
    toast.error(err instanceof ApiException ? err.apiError.message : 'Failed to load students');
  } finally {
    loading.value = false;
  }
}

function prevPage() {
  if (currentPage.value > 0) load(currentPage.value - 1);
}

function nextPage() {
  if (currentPage.value < totalPages.value - 1) load(currentPage.value + 1);
}

async function downloadTemplate() {
  try {
    const blob = await studentsApi.importTemplate();
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'student-import-template.csv';
    a.click();
    URL.revokeObjectURL(url);
  } catch (err) {
    toast.error('Failed to download template');
  }
}

function onFileChange(e: Event) {
  const target = e.target as HTMLInputElement;
  importFile.value = target.files?.[0] ?? null;
}

async function runImport() {
  if (!importFile.value) {
    toast.error('Please select a CSV file');
    return;
  }
  importing.value = true;
  importResult.value = null;
  try {
    importResult.value = await studentsApi.importCsv(importOrgId.value, importFile.value);
    toast.success(`Import complete: ${importResult.value.createdRows} created, ${importResult.value.skippedRows} skipped, ${importResult.value.failedRows} failed`);
    load(0);
  } catch (err) {
    toast.error(err instanceof ApiException ? err.apiError.message : 'Import failed');
  } finally {
    importing.value = false;
  }
}

async function downloadErrorReport() {
  if (!importResult.value?.jobId) return;
  try {
    const blob = await studentsApi.importErrors(importResult.value.jobId);
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `student-import-${importResult.value.jobId}-errors.csv`;
    a.click();
    URL.revokeObjectURL(url);
  } catch (err) {
    toast.error('Failed to download error report');
  }
}

function statusBadgeClass(status: string): string {
  switch (status) {
    case 'ACTIVE': return 'badge-ok';
    case 'GRADUATED': return 'badge-ok';
    case 'PROSPECT': return 'badge-neutral';
    case 'PAUSED': return 'badge-warn';
    case 'WITHDRAWN': return 'badge-off';
    default: return 'badge-neutral';
  }
}

onMounted(load);
</script>

<template>
  <section class="students">
    <header>
      <h1>Students</h1>
      <div class="actions">
        <button type="button" @click="downloadTemplate">Download Import Template</button>
        <button type="button" @click="showImport = !showImport">
          {{ showImport ? 'Hide Import' : 'Import Students' }}
        </button>
      </div>
    </header>

    <!-- Import panel -->
    <div v-if="showImport" class="import-panel">
      <h3>Bulk Import Students (CSV)</h3>
      <div class="import-form">
        <label>
          Organization ID:
          <input v-model.number="importOrgId" type="number" min="1" style="width: 80px" />
        </label>
        <label>
          CSV File:
          <input type="file" accept=".csv,text/csv" @change="onFileChange" />
        </label>
        <button type="button" :disabled="importing || !importFile" @click="runImport">
          {{ importing ? 'Importing...' : 'Upload & Import' }}
        </button>
      </div>
      <div v-if="importResult" class="import-result">
        <h4>Import Result (Job #{{ importResult.jobId }})</h4>
        <ul>
          <li>Total rows: {{ importResult.totalRows }}</li>
          <li>Created: {{ importResult.createdRows }}</li>
          <li>Skipped (duplicates): {{ importResult.skippedRows }}</li>
          <li>Failed: {{ importResult.failedRows }}</li>
        </ul>
        <div v-if="importResult.sampleErrors.length > 0">
          <h5>Sample Errors (first {{ importResult.sampleErrors.length }})</h5>
          <table class="table error-table">
            <thead><tr><th>Line</th><th>Code</th><th>Message</th></tr></thead>
            <tbody>
              <tr v-for="e in importResult.sampleErrors" :key="e.lineNumber">
                <td>{{ e.lineNumber }}</td><td>{{ e.code }}</td><td>{{ e.message }}</td>
              </tr>
            </tbody>
          </table>
          <button v-if="importResult.errorReportPath" type="button" @click="downloadErrorReport">
            Download Full Error Report
          </button>
        </div>
      </div>
    </div>

    <div v-if="loading && students.length === 0" class="placeholder">Loading...</div>

    <table v-else-if="students.length > 0" class="table">
      <thead>
        <tr>
          <th>Name</th>
          <th>External ID</th>
          <th>Email</th>
          <th>Phone</th>
          <th>Skill Level</th>
          <th>School</th>
          <th>Program</th>
          <th>Class</th>
          <th>Housing</th>
          <th>Status</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="s in students" :key="s.id">
          <td>{{ s.fullName }}</td>
          <td>{{ s.externalId ?? '---' }}</td>
          <td>{{ s.email ?? '---' }}</td>
          <td>{{ s.phone ?? '---' }}</td>
          <td>{{ s.skillLevel ?? '---' }}</td>
          <td>{{ s.school ?? '---' }}</td>
          <td>{{ s.program ?? '---' }}</td>
          <td>{{ s.classGroup ?? '---' }}</td>
          <td>{{ s.housingAssignment ?? '---' }}</td>
          <td>
            <span :class="['badge', statusBadgeClass(s.enrollmentStatus)]">
              {{ s.enrollmentStatus }}
            </span>
          </td>
        </tr>
      </tbody>
    </table>

    <div v-else class="placeholder">No students found within your data scope.</div>

    <div v-if="totalPages > 1" class="pagination">
      <button :disabled="currentPage === 0" @click="prevPage">Previous</button>
      <span>Page {{ currentPage + 1 }} of {{ totalPages }} ({{ totalElements }} total)</span>
      <button :disabled="currentPage >= totalPages - 1" @click="nextPage">Next</button>
    </div>
  </section>
</template>

<style scoped>
.students h1 { margin: 0 0 4px; }
.actions { margin: 8px 0 16px; display: flex; gap: 8px; }
.actions button { padding: 6px 14px; border: 1px solid var(--color-border); border-radius: var(--radius); background: var(--color-surface); cursor: pointer; }
.import-panel { background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius); padding: 16px; margin-bottom: 16px; }
.import-panel h3 { margin: 0 0 12px; font-size: 15px; }
.import-form { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.import-form label { display: flex; align-items: center; gap: 6px; font-size: 13px; }
.import-form input { padding: 4px 8px; border: 1px solid var(--color-border); border-radius: var(--radius); }
.import-form button { padding: 6px 14px; border: 1px solid var(--color-border); border-radius: var(--radius); background: var(--color-primary); color: #fff; cursor: pointer; }
.import-form button:disabled { opacity: 0.5; cursor: not-allowed; }
.import-result { margin-top: 12px; font-size: 13px; }
.import-result h4 { margin: 0 0 8px; }
.import-result ul { margin: 0 0 8px; padding-left: 20px; }
.import-result h5 { margin: 8px 0 4px; }
.error-table { margin-bottom: 8px; }
.table { width: 100%; border-collapse: collapse; background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius); overflow: hidden; }
.table th, .table td { text-align: left; padding: 8px 10px; border-bottom: 1px solid var(--color-border); font-size: 13px; }
.table thead th { background: var(--color-bg); font-weight: 600; }
.badge { display: inline-block; padding: 2px 8px; border-radius: 999px; font-size: 11px; font-weight: 600; }
.badge-ok { background: #e6f6ed; color: #157a3c; }
.badge-off { background: #fbeaea; color: #a0282a; }
.badge-warn { background: #fef3cd; color: #856404; }
.badge-neutral { background: #e2e3e5; color: #383d41; }
.placeholder { padding: 24px; text-align: center; color: var(--color-text-muted); background: var(--color-surface); border: 1px dashed var(--color-border); border-radius: var(--radius); }
.pagination { display: flex; align-items: center; justify-content: center; gap: 12px; margin-top: 16px; font-size: 13px; }
.pagination button { padding: 6px 14px; border: 1px solid var(--color-border); border-radius: var(--radius); background: var(--color-surface); cursor: pointer; }
.pagination button:disabled { opacity: 0.5; cursor: not-allowed; }
</style>
