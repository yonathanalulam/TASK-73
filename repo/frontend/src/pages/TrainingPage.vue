<script setup lang="ts">
import { onMounted, ref, computed } from 'vue';
import { trainingApi, type TrainingSession, type Booking, type BookingStatus } from '@/api/training';
import { ApiException } from '@/api/client';
import { useToast } from '@/composables/useToast';
import { useAuthStore } from '@/stores/auth';

const toast = useToast();
const auth = useAuthStore();
const loading = ref(false);
const sessions = ref<TrainingSession[]>([]);
const myBookings = ref<Booking[]>([]);

// Filters
const filterType = ref<string>('');
const filterLevel = ref<string>('');
const filterStyle = ref<string>('');

// Booking form
const showBookForm = ref(false);
const bookSessionId = ref<number | null>(null);
const bookStudentId = ref<number>(0);
const bookNotes = ref('');
const bookingInProgress = ref(false);

const canManageBookings = computed(() => auth.hasPermission('bookings.write'));

const filteredSessions = computed(() => {
  return sessions.value.filter(s => {
    if (filterType.value && s.sessionType !== filterType.value) return false;
    if (filterLevel.value && s.level !== filterLevel.value) return false;
    if (filterStyle.value && s.style !== filterStyle.value) return false;
    return true;
  });
});

const uniqueLevels = computed(() => [...new Set(
  sessions.value
    .map(s => s.level)
    .filter((level): level is string => Boolean(level)),
)]);
const uniqueStyles = computed(() => [...new Set(
  sessions.value
    .map(s => s.style)
    .filter((style): style is string => Boolean(style)),
)]);

async function load() {
  loading.value = true;
  try {
    sessions.value = await trainingApi.listSessions();
    if (auth.hasPermission('bookings.read')) {
      try {
        myBookings.value = await trainingApi.listMyBookings();
      } catch { /* non-critical */ }
    }
  } catch (err) {
    toast.error(err instanceof ApiException ? err.apiError.message : 'Failed to load sessions');
  } finally {
    loading.value = false;
  }
}

function openBookForm(sessionId: number) {
  bookSessionId.value = sessionId;
  bookNotes.value = '';
  showBookForm.value = true;
}

async function submitBooking() {
  if (!bookSessionId.value) return;
  bookingInProgress.value = true;
  try {
    await trainingApi.createBooking({
      trainingSessionId: bookSessionId.value,
      studentId: bookStudentId.value,
      notes: bookNotes.value || undefined,
    });
    toast.success('Booking created');
    showBookForm.value = false;
    load();
  } catch (err) {
    if (err instanceof ApiException) {
      if (err.apiError.code === 'BOOKING_CONFLICT') {
        toast.error('Conflict: You already have an overlapping booking at this time');
      } else if (err.apiError.code === 'SESSION_FULL') {
        toast.error('This session is full');
      } else if (err.apiError.code === 'FEATURE_DISABLED') {
        toast.error('Bookings are currently in read-only mode');
      } else {
        toast.error(err.apiError.message);
      }
    } else {
      toast.error('Failed to create booking');
    }
  } finally {
    bookingInProgress.value = false;
  }
}

async function confirmBooking(id: number) {
  try {
    await trainingApi.confirmBooking(id);
    toast.success('Booking confirmed');
    load();
  } catch (err) {
    toast.error(err instanceof ApiException ? err.apiError.message : 'Failed to confirm');
  }
}

async function cancelBooking(id: number) {
  try {
    await trainingApi.cancelBooking(id);
    toast.success('Booking cancelled');
    load();
  } catch (err) {
    toast.error(err instanceof ApiException ? err.apiError.message : 'Failed to cancel');
  }
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString();
}

function statusLabel(status: string): string {
  switch (status) {
    case 'SCHEDULED': return 'Scheduled';
    case 'IN_PROGRESS': return 'In Progress';
    case 'COMPLETED': return 'Completed';
    case 'CANCELLED': return 'Cancelled';
    default: return status;
  }
}

function statusBadgeClass(status: string): string {
  switch (status) {
    case 'SCHEDULED': return 'badge-ok';
    case 'IN_PROGRESS': return 'badge-active';
    case 'COMPLETED': return 'badge-neutral';
    case 'CANCELLED': return 'badge-off';
    default: return 'badge-neutral';
  }
}

function bookingStatusClass(status: BookingStatus): string {
  switch (status) {
    case 'INITIATED': return 'badge-warn';
    case 'CONFIRMED': case 'BOOKED': case 'CHECKED_IN': return 'badge-ok';
    case 'CANCELED': case 'CANCELLED': return 'badge-off';
    case 'REFUNDED': return 'badge-neutral';
    case 'NO_SHOW': return 'badge-off';
    default: return 'badge-neutral';
  }
}

function sessionTypeLabel(s: TrainingSession): string {
  if (s.sessionType === 'ONLINE') return 'Online';
  if (s.sessionType === 'VENUE') return 'In-Person';
  return '';
}

function canBook(s: TrainingSession): boolean {
  return s.status === 'SCHEDULED' && s.bookedSeats < s.capacity;
}

onMounted(load);
</script>

<template>
  <section class="training">
    <header>
      <h1>Training Sessions</h1>
      <p class="muted">
        Browse available training and sparring sessions. Staff can manage sessions;
        students can view and book.
      </p>
    </header>

    <!-- Filters -->
    <div class="filters">
      <label>
        Type:
        <select v-model="filterType">
          <option value="">All</option>
          <option value="VENUE">In-Person</option>
          <option value="ONLINE">Online</option>
        </select>
      </label>
      <label v-if="uniqueLevels.length">
        Level:
        <select v-model="filterLevel">
          <option value="">All</option>
          <option v-for="l in uniqueLevels" :key="l" :value="l">{{ l }}</option>
        </select>
      </label>
      <label v-if="uniqueStyles.length">
        Style:
        <select v-model="filterStyle">
          <option value="">All</option>
          <option v-for="s in uniqueStyles" :key="s" :value="s">{{ s }}</option>
        </select>
      </label>
    </div>

    <div v-if="loading && sessions.length === 0" class="placeholder">Loading...</div>

    <div v-else-if="filteredSessions.length > 0" class="grid">
      <div v-for="s in filteredSessions" :key="s.id" class="card">
        <div class="card-header">
          <h3>Session #{{ s.id }}</h3>
          <span :class="['badge', statusBadgeClass(s.status)]">
            {{ statusLabel(s.status) }}
          </span>
        </div>
        <p v-if="s.notes" class="desc">{{ s.notes }}</p>
        <div class="meta">
          <div><strong>When:</strong> {{ formatDate(s.startsAt) }} – {{ formatDate(s.endsAt) }}</div>
          <div><strong>Capacity:</strong> {{ s.bookedSeats }} / {{ s.capacity }} booked
            <span v-if="s.bookedSeats >= s.capacity" class="badge badge-off">Full</span>
          </div>
          <div v-if="s.sessionType"><strong>Type:</strong> {{ sessionTypeLabel(s) }}</div>
          <div v-if="s.level"><strong>Level:</strong> {{ s.level }}</div>
          <div v-if="s.style"><strong>Style:</strong> {{ s.style }}</div>
          <div v-if="s.weightClassLbs"><strong>Weight class:</strong> {{ s.weightClassLbs }} lbs</div>
          <div v-if="s.location"><strong>Location:</strong> {{ s.location }}</div>
          <div v-if="s.onlineUrl"><strong>Online:</strong> {{ s.onlineUrl }}</div>
          <div v-if="s.cancelledAt"><strong>Cancelled:</strong> {{ formatDate(s.cancelledAt) }}</div>
        </div>
        <div v-if="canManageBookings && canBook(s)" class="card-actions">
          <button type="button" @click="openBookForm(s.id)">Book Student</button>
        </div>
      </div>
    </div>

    <div v-else class="placeholder">No training sessions found matching your filters.</div>

    <!-- Booking form dialog -->
    <div v-if="showBookForm" class="modal-overlay" @click.self="showBookForm = false">
      <div class="modal">
        <h3>Create Booking for Session #{{ bookSessionId }}</h3>
        <form @submit.prevent="submitBooking">
          <label>
            Student ID:
            <input v-model.number="bookStudentId" type="number" min="1" required />
          </label>
          <label>
            Notes (optional):
            <input v-model="bookNotes" maxlength="500" />
          </label>
          <div class="modal-actions">
            <button type="submit" :disabled="bookingInProgress">
              {{ bookingInProgress ? 'Booking...' : 'Create Booking' }}
            </button>
            <button type="button" @click="showBookForm = false">Cancel</button>
          </div>
        </form>
      </div>
    </div>

    <!-- My Bookings -->
    <div v-if="myBookings.length > 0" class="my-bookings">
      <h2>My Bookings</h2>
      <table class="table">
        <thead>
          <tr>
            <th>Booking ID</th>
            <th>Session</th>
            <th>Status</th>
            <th>Type</th>
            <th>Created</th>
            <th v-if="canManageBookings">Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="b in myBookings" :key="b.id">
            <td>#{{ b.id }}</td>
            <td>#{{ b.sessionId }}</td>
            <td>
              <span :class="['badge', bookingStatusClass(b.status)]">{{ b.status }}</span>
            </td>
            <td>{{ b.sessionType ?? '---' }}</td>
            <td>{{ formatDate(b.createdAt) }}</td>
            <td v-if="canManageBookings">
              <button v-if="b.status === 'INITIATED'" type="button" @click="confirmBooking(b.id)" class="btn-sm">Confirm</button>
              <button v-if="b.status === 'INITIATED' || b.status === 'CONFIRMED'" type="button" @click="cancelBooking(b.id)" class="btn-sm btn-danger">Cancel</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<style scoped>
.training h1 { margin: 0 0 4px; }
.muted { color: var(--color-text-muted); margin: 0 0 16px; }
.filters { display: flex; gap: 12px; margin-bottom: 16px; flex-wrap: wrap; }
.filters label { display: flex; align-items: center; gap: 6px; font-size: 13px; }
.filters select { padding: 4px 8px; border: 1px solid var(--color-border); border-radius: var(--radius); }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 16px; }
.card { background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius); padding: 16px; box-shadow: var(--shadow); }
.card-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 8px; }
.card h3 { margin: 0; font-size: 15px; }
.desc { margin: 0 0 8px; font-size: 13px; color: var(--color-text-muted); }
.meta { font-size: 13px; display: flex; flex-direction: column; gap: 2px; }
.card-actions { margin-top: 12px; }
.card-actions button { padding: 6px 14px; border: 1px solid var(--color-primary); border-radius: var(--radius); background: var(--color-primary); color: #fff; cursor: pointer; font-size: 13px; }
.badge { display: inline-block; padding: 2px 8px; border-radius: 999px; font-size: 11px; font-weight: 600; white-space: nowrap; }
.badge-ok { background: #e6f6ed; color: #157a3c; }
.badge-active { background: #cce5ff; color: #004085; }
.badge-off { background: #fbeaea; color: #a0282a; }
.badge-warn { background: #fef3cd; color: #856404; }
.badge-neutral { background: #e2e3e5; color: #383d41; }
.placeholder { padding: 24px; text-align: center; color: var(--color-text-muted); background: var(--color-surface); border: 1px dashed var(--color-border); border-radius: var(--radius); }
.modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.4); display: flex; align-items: center; justify-content: center; z-index: 100; }
.modal { background: var(--color-surface); border-radius: var(--radius); padding: 24px; min-width: 360px; max-width: 90vw; }
.modal h3 { margin: 0 0 16px; }
.modal label { display: flex; flex-direction: column; gap: 4px; font-size: 13px; margin-bottom: 12px; }
.modal input { padding: 6px 10px; border: 1px solid var(--color-border); border-radius: var(--radius); }
.modal-actions { display: flex; gap: 8px; }
.modal-actions button { padding: 8px 18px; border: 1px solid var(--color-border); border-radius: var(--radius); cursor: pointer; }
.modal-actions button:first-child { background: var(--color-primary); color: #fff; border-color: var(--color-primary); }
.modal-actions button:disabled { opacity: 0.5; cursor: not-allowed; }
.my-bookings { margin-top: 32px; }
.my-bookings h2 { font-size: 17px; margin: 0 0 12px; }
.table { width: 100%; border-collapse: collapse; background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius); overflow: hidden; }
.table th, .table td { text-align: left; padding: 8px 10px; border-bottom: 1px solid var(--color-border); font-size: 13px; }
.table thead th { background: var(--color-bg); font-weight: 600; }
.btn-sm { padding: 3px 10px; font-size: 12px; border: 1px solid var(--color-border); border-radius: var(--radius); background: var(--color-surface); cursor: pointer; margin-right: 4px; }
.btn-danger { color: #a0282a; border-color: #a0282a; }
</style>
