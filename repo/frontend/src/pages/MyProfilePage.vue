<script setup lang="ts">
import { onMounted, ref, reactive } from 'vue';
import { studentsApi, type Student, type UpdateStudentSelfRequest } from '@/api/students';
import { ApiException } from '@/api/client';
import { useToast } from '@/composables/useToast';

const toast = useToast();
const loading = ref(false);
const saving = ref(false);
const editing = ref(false);
const profile = ref<Student | null>(null);
const form = reactive<UpdateStudentSelfRequest>({});

async function load() {
  loading.value = true;
  try {
    profile.value = await studentsApi.getMyProfile();
    resetForm();
  } catch (err) {
    if (err instanceof ApiException && err.apiError.code === 'NOT_FOUND') {
      profile.value = null;
    } else {
      toast.error(err instanceof ApiException ? err.apiError.message : 'Failed to load profile');
    }
  } finally {
    loading.value = false;
  }
}

function resetForm() {
  if (!profile.value) return;
  form.fullName = profile.value.fullName;
  form.email = profile.value.email ?? '';
  form.phone = profile.value.phone ?? '';
  form.emergencyContactName = profile.value.emergencyContactName ?? '';
  form.emergencyContactPhone = profile.value.emergencyContactPhone ?? '';
  form.notes = profile.value.notes ?? '';
}

function startEdit() {
  resetForm();
  editing.value = true;
}

function cancelEdit() {
  editing.value = false;
  resetForm();
}

async function save() {
  saving.value = true;
  try {
    profile.value = await studentsApi.updateMyProfile(form);
    editing.value = false;
    toast.success('Profile updated');
  } catch (err) {
    toast.error(err instanceof ApiException ? err.apiError.message : 'Failed to save profile');
  } finally {
    saving.value = false;
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
  <section class="profile">
    <header>
      <h1>My Profile</h1>
    </header>

    <div v-if="loading" class="placeholder">Loading...</div>

    <div v-else-if="!profile" class="placeholder">
      No student profile is linked to your account. Please contact staff.
    </div>

    <div v-else class="card">
      <!-- Read-only view -->
      <template v-if="!editing">
        <div class="field-grid">
          <div class="field"><label>Name</label><span>{{ profile.fullName }}</span></div>
          <div class="field"><label>Email</label><span>{{ profile.email ?? '---' }}</span></div>
          <div class="field"><label>Phone</label><span>{{ profile.phone ?? '---' }}</span></div>
          <div class="field"><label>Emergency Contact</label><span>{{ profile.emergencyContactName ?? '---' }}</span></div>
          <div class="field"><label>Emergency Phone</label><span>{{ profile.emergencyContactPhone ?? '---' }}</span></div>
          <div class="field"><label>Enrollment Status</label>
            <span :class="['badge', statusBadgeClass(profile.enrollmentStatus)]">{{ profile.enrollmentStatus }}</span>
          </div>
          <div class="field"><label>Skill Level</label><span>{{ profile.skillLevel ?? '---' }}</span></div>
          <div class="field"><label>School</label><span>{{ profile.school ?? '---' }}</span></div>
          <div class="field"><label>Program</label><span>{{ profile.program ?? '---' }}</span></div>
          <div class="field"><label>Class Group</label><span>{{ profile.classGroup ?? '---' }}</span></div>
          <div class="field"><label>Housing</label><span>{{ profile.housingAssignment ?? '---' }}</span></div>
          <div class="field"><label>Notes</label><span>{{ profile.notes ?? '---' }}</span></div>
        </div>
        <div class="actions">
          <button type="button" @click="startEdit">Edit Profile</button>
        </div>
      </template>

      <!-- Edit form -->
      <template v-else>
        <form @submit.prevent="save" class="field-grid">
          <div class="field">
            <label for="fullName">Name</label>
            <input id="fullName" v-model="form.fullName" maxlength="128" required />
          </div>
          <div class="field">
            <label for="email">Email</label>
            <input id="email" v-model="form.email" type="email" maxlength="190" />
          </div>
          <div class="field">
            <label for="phone">Phone</label>
            <input id="phone" v-model="form.phone" maxlength="32" />
          </div>
          <div class="field">
            <label for="ecName">Emergency Contact</label>
            <input id="ecName" v-model="form.emergencyContactName" maxlength="128" />
          </div>
          <div class="field">
            <label for="ecPhone">Emergency Phone</label>
            <input id="ecPhone" v-model="form.emergencyContactPhone" maxlength="32" />
          </div>
          <div class="field">
            <label for="notes">Notes</label>
            <textarea id="notes" v-model="form.notes" maxlength="1024" rows="3"></textarea>
          </div>
          <!-- Read-only fields shown for context -->
          <div class="field"><label>Enrollment Status</label>
            <span :class="['badge', statusBadgeClass(profile.enrollmentStatus)]">{{ profile.enrollmentStatus }}</span>
            <small class="hint">Managed by staff</small>
          </div>
          <div class="field"><label>Skill Level</label><span>{{ profile.skillLevel ?? '---' }}</span><small class="hint">Managed by staff</small></div>
          <div class="field"><label>School</label><span>{{ profile.school ?? '---' }}</span><small class="hint">Managed by staff</small></div>
          <div class="actions">
            <button type="submit" :disabled="saving">{{ saving ? 'Saving...' : 'Save' }}</button>
            <button type="button" @click="cancelEdit" :disabled="saving">Cancel</button>
          </div>
        </form>
      </template>
    </div>
  </section>
</template>

<style scoped>
.profile h1 { margin: 0 0 16px; }
.card { background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius); padding: 20px; }
.field-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px 24px; }
.field { display: flex; flex-direction: column; gap: 2px; }
.field label { font-size: 12px; font-weight: 600; color: var(--color-text-muted); text-transform: uppercase; letter-spacing: 0.5px; }
.field span { font-size: 14px; }
.field input, .field textarea { font-size: 14px; padding: 6px 10px; border: 1px solid var(--color-border); border-radius: var(--radius); }
.hint { font-size: 11px; color: var(--color-text-muted); }
.actions { grid-column: 1 / -1; display: flex; gap: 8px; margin-top: 12px; }
.actions button { padding: 8px 18px; border: 1px solid var(--color-border); border-radius: var(--radius); background: var(--color-surface); cursor: pointer; font-weight: 500; }
.actions button:first-child { background: var(--color-primary); color: #fff; border-color: var(--color-primary); }
.actions button:disabled { opacity: 0.5; cursor: not-allowed; }
.badge { display: inline-block; padding: 2px 8px; border-radius: 999px; font-size: 11px; font-weight: 600; }
.badge-ok { background: #e6f6ed; color: #157a3c; }
.badge-off { background: #fbeaea; color: #a0282a; }
.badge-warn { background: #fef3cd; color: #856404; }
.badge-neutral { background: #e2e3e5; color: #383d41; }
.placeholder { padding: 24px; text-align: center; color: var(--color-text-muted); background: var(--color-surface); border: 1px dashed var(--color-border); border-radius: var(--radius); }
</style>
