<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useAuthStore } from '@/stores/auth';
import { notificationsApi, type Notification } from '@/api/notifications';
import { trainingApi, type TrainingSession } from '@/api/training';
import { studentsApi, type Student } from '@/api/students';
import { moderationApi, type ModerationReport } from '@/api/moderation';
import { usersApi } from '@/api/users';

const auth = useAuthStore();

const greeting = computed(() => {
  const hour = new Date().getHours();
  if (hour < 12) return 'Good morning';
  if (hour < 18) return 'Good afternoon';
  return 'Good evening';
});

// Shared data
const notifications = ref<Notification[]>([]);
const unreadCount = ref(0);

// Student data
const upcomingSessions = ref<TrainingSession[]>([]);

// Staff data
const openReports = ref<ModerationReport[]>([]);
const todaySessions = ref<TrainingSession[]>([]);
const recentStudents = ref<Student[]>([]);

// Admin data
const totalUsers = ref(0);

const loading = ref(true);

function isUpcoming(session: TrainingSession): boolean {
  return new Date(session.startsAt) > new Date() &&
    (session.status === 'SCHEDULED' || session.status === 'IN_PROGRESS');
}

function isToday(iso: string): boolean {
  const d = new Date(iso);
  const now = new Date();
  return d.getFullYear() === now.getFullYear() &&
    d.getMonth() === now.getMonth() &&
    d.getDate() === now.getDate();
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString();
}

function formatShortDate(iso: string): string {
  return new Date(iso).toLocaleDateString();
}

async function loadDashboard() {
  loading.value = true;
  const role = auth.primaryRole;

  try {
    // Load notifications for everyone
    const [notifList, unread] = await Promise.all([
      notificationsApi.list().catch(() => [] as Notification[]),
      notificationsApi.unreadCount().catch(() => ({ unread: 0 })),
    ]);
    notifications.value = notifList.slice(0, 5);
    unreadCount.value = unread.unread;

    if (role === 'STUDENT') {
      const sessions = await trainingApi.listSessions().catch(() => []);
      upcomingSessions.value = sessions.filter(isUpcoming).slice(0, 5);
    }

    if (role === 'STAFF' || role === 'ADMIN') {
      const [reports, sessions, studentPage] = await Promise.all([
        moderationApi.listOpen().catch(() => []),
        trainingApi.listSessions().catch(() => []),
        studentsApi.list(0, 5).catch(() => ({ content: [] as Student[], totalElements: 0, totalPages: 0, number: 0, size: 5, first: true, last: true })),
      ]);
      openReports.value = reports.slice(0, 5);
      todaySessions.value = sessions.filter(s => isToday(s.startsAt)).slice(0, 5);
      recentStudents.value = studentPage.content;
    }

    if (role === 'ADMIN') {
      const userPage = await usersApi.list({ page: 0, size: 1 }).catch(() => ({
        content: [], totalElements: 0, totalPages: 0, number: 0, size: 1, first: true, last: true,
      }));
      totalUsers.value = userPage.totalElements;
    }
  } finally {
    loading.value = false;
  }
}

onMounted(loadDashboard);
</script>

<template>
  <section class="dashboard">
    <header>
      <h1>{{ greeting }}, {{ auth.user?.fullName }}</h1>
      <p class="muted">
        Signed in as
        <strong>{{ auth.user?.username }}</strong>
        ({{ auth.user?.primaryRole }})
      </p>
    </header>

    <div v-if="loading" class="placeholder">Loading dashboard...</div>

    <template v-else>
      <!-- ========== STUDENT DASHBOARD ========== -->
      <div v-if="auth.primaryRole === 'STUDENT'" class="grid">
        <div class="card">
          <h3>Upcoming Training</h3>
          <div v-if="upcomingSessions.length > 0" class="card-list">
            <div v-for="s in upcomingSessions" :key="s.id" class="card-list-item">
              <span class="item-main">Session #{{ s.id }} — {{ s.style || 'General' }}</span>
              <span class="item-sub">{{ formatDate(s.startsAt) }}</span>
              <span :class="['mini-badge', s.status === 'SCHEDULED' ? 'badge-ok' : 'badge-active']">
                {{ s.status }}
              </span>
            </div>
          </div>
          <p v-else class="empty">No upcoming sessions.</p>
        </div>

        <div class="card">
          <h3>Recent Notifications</h3>
          <div v-if="notifications.length > 0" class="card-list">
            <div v-for="n in notifications" :key="n.id" class="card-list-item">
              <span class="item-main">{{ n.title }}</span>
              <span class="item-sub">{{ formatShortDate(n.createdAt) }}</span>
              <span v-if="!n.readAt" class="unread-dot" />
            </div>
          </div>
          <p v-else class="empty">No notifications.</p>
        </div>

        <div class="card stat-card">
          <h3>Unread Notifications</h3>
          <div class="stat-value">{{ unreadCount }}</div>
        </div>
      </div>

      <!-- ========== PHOTOGRAPHER DASHBOARD ========== -->
      <div v-else-if="auth.primaryRole === 'PHOTOGRAPHER'" class="grid">
        <div class="card">
          <h3>Upcoming Sessions</h3>
          <div v-if="upcomingSessions.length > 0" class="card-list">
            <div v-for="s in upcomingSessions" :key="s.id" class="card-list-item">
              <span class="item-main">Session #{{ s.id }}</span>
              <span class="item-sub">{{ formatDate(s.startsAt) }} — {{ s.location || 'Online' }}</span>
            </div>
          </div>
          <p v-else class="empty">No upcoming events.</p>
        </div>

        <div class="card">
          <h3>Notifications</h3>
          <div v-if="notifications.length > 0" class="card-list">
            <div v-for="n in notifications" :key="n.id" class="card-list-item">
              <span class="item-main">{{ n.title }}</span>
              <span class="item-sub">{{ formatShortDate(n.createdAt) }}</span>
              <span v-if="!n.readAt" class="unread-dot" />
            </div>
          </div>
          <p v-else class="empty">No notifications.</p>
        </div>

        <div class="card stat-card">
          <h3>Unread</h3>
          <div class="stat-value">{{ unreadCount }}</div>
        </div>
      </div>

      <!-- ========== STAFF DASHBOARD ========== -->
      <div v-else-if="auth.primaryRole === 'STAFF'" class="grid">
        <div class="card">
          <h3>Moderation Queue</h3>
          <div v-if="openReports.length > 0" class="card-list">
            <div v-for="r in openReports" :key="r.id" class="card-list-item">
              <span class="item-main">{{ r.targetType }} #{{ r.targetId }} — {{ r.reason }}</span>
              <span class="item-sub">Reported {{ formatShortDate(r.createdAt) }}</span>
              <span class="mini-badge badge-warn">{{ r.status }}</span>
            </div>
          </div>
          <p v-else class="empty">No open reports.</p>
        </div>

        <div class="card">
          <h3>Today's Sessions</h3>
          <div v-if="todaySessions.length > 0" class="card-list">
            <div v-for="s in todaySessions" :key="s.id" class="card-list-item">
              <span class="item-main">Session #{{ s.id }} — {{ s.style || 'General' }}</span>
              <span class="item-sub">{{ formatDate(s.startsAt) }} ({{ s.bookedSeats }}/{{ s.capacity }})</span>
            </div>
          </div>
          <p v-else class="empty">No sessions today.</p>
        </div>

        <div class="card">
          <h3>Recent Students</h3>
          <div v-if="recentStudents.length > 0" class="card-list">
            <div v-for="s in recentStudents" :key="s.id" class="card-list-item">
              <span class="item-main">{{ s.fullName }}</span>
              <span class="item-sub">{{ s.enrollmentStatus }} — {{ s.school || 'No school' }}</span>
            </div>
          </div>
          <p v-else class="empty">No students in scope.</p>
        </div>

        <div class="card stat-card">
          <h3>Unread Notifications</h3>
          <div class="stat-value">{{ unreadCount }}</div>
        </div>
      </div>

      <!-- ========== ADMIN DASHBOARD ========== -->
      <div v-else-if="auth.primaryRole === 'ADMIN'" class="grid">
        <div class="card stat-card">
          <h3>Total Users</h3>
          <div class="stat-value">{{ totalUsers }}</div>
        </div>

        <div class="card stat-card">
          <h3>Open Moderation Reports</h3>
          <div class="stat-value">{{ openReports.length }}</div>
        </div>

        <div class="card stat-card">
          <h3>Unread Notifications</h3>
          <div class="stat-value">{{ unreadCount }}</div>
        </div>

        <div class="card">
          <h3>Moderation Queue</h3>
          <div v-if="openReports.length > 0" class="card-list">
            <div v-for="r in openReports" :key="r.id" class="card-list-item">
              <span class="item-main">{{ r.targetType }} #{{ r.targetId }} — {{ r.reason }}</span>
              <span class="item-sub">Reported {{ formatShortDate(r.createdAt) }}</span>
              <span class="mini-badge badge-warn">{{ r.status }}</span>
            </div>
          </div>
          <p v-else class="empty">No open reports.</p>
        </div>

        <div class="card">
          <h3>Today's Training Sessions</h3>
          <div v-if="todaySessions.length > 0" class="card-list">
            <div v-for="s in todaySessions" :key="s.id" class="card-list-item">
              <span class="item-main">Session #{{ s.id }} — {{ s.style || 'General' }}</span>
              <span class="item-sub">{{ formatDate(s.startsAt) }} ({{ s.bookedSeats }}/{{ s.capacity }})</span>
            </div>
          </div>
          <p v-else class="empty">No sessions today.</p>
        </div>

        <div class="card">
          <h3>Recent Students</h3>
          <div v-if="recentStudents.length > 0" class="card-list">
            <div v-for="s in recentStudents" :key="s.id" class="card-list-item">
              <span class="item-main">{{ s.fullName }}</span>
              <span class="item-sub">{{ s.enrollmentStatus }} — {{ s.school || 'No school' }}</span>
            </div>
          </div>
          <p v-else class="empty">No students in scope.</p>
        </div>
      </div>

      <!-- ========== FALLBACK ========== -->
      <div v-else class="placeholder">
        Unknown role. Please contact an administrator.
      </div>
    </template>
  </section>
</template>

<style scoped>
.dashboard h1 { margin: 0 0 4px; }
.muted { color: var(--color-text-muted); margin: 0 0 24px; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 16px; }
.card {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  padding: 16px;
  box-shadow: var(--shadow);
}
.card h3 { margin: 0 0 12px; font-size: 15px; }
.stat-card { text-align: center; }
.stat-value { font-size: 36px; font-weight: 700; color: var(--color-primary); margin: 8px 0; }
.card-list { display: flex; flex-direction: column; gap: 8px; }
.card-list-item {
  display: flex; flex-wrap: wrap; align-items: center; gap: 6px;
  padding: 6px 0; border-bottom: 1px solid var(--color-border);
  font-size: 13px;
}
.card-list-item:last-child { border-bottom: none; }
.item-main { font-weight: 500; flex: 1; min-width: 120px; }
.item-sub { color: var(--color-text-muted); font-size: 12px; }
.mini-badge { display: inline-block; padding: 1px 6px; border-radius: 999px; font-size: 10px; font-weight: 600; }
.badge-ok { background: #e6f6ed; color: #157a3c; }
.badge-active { background: #cce5ff; color: #004085; }
.badge-warn { background: #fef3cd; color: #856404; }
.unread-dot { width: 8px; height: 8px; background: var(--color-primary); border-radius: 50%; flex-shrink: 0; }
.empty { margin: 0; font-size: 13px; color: var(--color-text-muted); }
.placeholder {
  padding: 24px; text-align: center;
  color: var(--color-text-muted);
  background: var(--color-surface);
  border: 1px dashed var(--color-border);
  border-radius: var(--radius);
}
</style>
