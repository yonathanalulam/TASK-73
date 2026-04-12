import { onMounted, onUnmounted, ref } from 'vue';
import { http } from '@/api/client';

/**
 * Tracks whether the local network / backend is reachable.
 *
 * Combines two signals:
 *  - browser navigator.onLine (link-layer)
 *  - periodic ping to /actuator/health (does the LAN backend actually respond)
 *
 * The "offline-first" expectation in the spec is that the UI degrades gracefully
 * when the backend is temporarily unreachable, so this drives the OfflineIndicator
 * banner and lets pages decide whether to show stale-data notices.
 */
export function useNetworkStatus(intervalMs = 15000) {
  const online = ref<boolean>(navigator.onLine);
  const backendReachable = ref<boolean>(true);
  let timer: number | null = null;

  async function pingBackend(): Promise<void> {
    try {
      await http.get('/actuator/health');
      backendReachable.value = true;
    } catch {
      backendReachable.value = false;
    }
  }

  function handleOnline() { online.value = true; void pingBackend(); }
  function handleOffline() { online.value = false; backendReachable.value = false; }

  onMounted(() => {
    window.addEventListener('online', handleOnline);
    window.addEventListener('offline', handleOffline);
    void pingBackend();
    timer = window.setInterval(() => { void pingBackend(); }, intervalMs);
  });

  onUnmounted(() => {
    window.removeEventListener('online', handleOnline);
    window.removeEventListener('offline', handleOffline);
    if (timer !== null) window.clearInterval(timer);
  });

  return { online, backendReachable };
}
