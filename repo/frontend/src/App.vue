<script setup lang="ts">
import { onErrorCaptured, ref } from 'vue';
import ToastContainer from '@/components/ToastContainer.vue';
import OfflineIndicator from '@/components/OfflineIndicator.vue';
import { useToast } from '@/composables/useToast';

const toast = useToast();
const fatalError = ref<string | null>(null);

// Top-level error boundary. Any uncaught error from a child component lands here.
// Returning false stops propagation. We surface a friendly message + a toast for the
// trace id so users can quote it when reporting issues.
onErrorCaptured((err) => {
  // eslint-disable-next-line no-console
  console.error('[App] uncaught error:', err);
  toast.error('Something went wrong. Please try again or refresh the page.');
  fatalError.value = err instanceof Error ? err.message : String(err);
  return false;
});
</script>

<template>
  <div class="app-root">
    <OfflineIndicator />
    <RouterView />
    <ToastContainer />

    <div v-if="fatalError" class="fatal-overlay" role="alert">
      <div class="fatal-card">
        <h2>An unexpected error occurred</h2>
        <p>{{ fatalError }}</p>
        <button class="primary" @click="fatalError = null">Dismiss</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.app-root {
  min-height: 100%;
  display: flex;
  flex-direction: column;
}
.fatal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}
.fatal-card {
  background: var(--color-surface);
  padding: 24px;
  border-radius: var(--radius);
  max-width: 480px;
  box-shadow: var(--shadow);
}
</style>
