<script setup lang="ts">
import { useToast } from '@/composables/useToast';

const toast = useToast();
</script>

<template>
  <div class="toast-stack" role="status" aria-live="polite">
    <div
      v-for="t in toast.toasts"
      :key="t.id"
      class="toast"
      :class="['toast-' + t.variant]"
      @click="toast.dismiss(t.id)"
    >
      {{ t.message }}
    </div>
  </div>
</template>

<style scoped>
.toast-stack {
  position: fixed;
  top: 16px;
  right: 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  z-index: 900;
}
.toast {
  padding: 10px 14px;
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  background: var(--color-surface);
  border-left: 4px solid var(--color-text-muted);
  cursor: pointer;
  max-width: 360px;
}
.toast-info { border-left-color: var(--color-primary); }
.toast-success { border-left-color: var(--color-success); }
.toast-warning { border-left-color: var(--color-warning); }
.toast-error { border-left-color: var(--color-danger); }
</style>
