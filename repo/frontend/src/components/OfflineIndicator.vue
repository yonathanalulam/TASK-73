<script setup lang="ts">
import { computed } from 'vue';
import { useNetworkStatus } from '@/composables/useNetworkStatus';

const { online, backendReachable } = useNetworkStatus();

const message = computed(() => {
  if (!online.value) return 'You appear to be offline. Some features may be unavailable.';
  if (!backendReachable.value)
    return 'Cannot reach the DojoStay server on the local network. Retrying…';
  return null;
});
</script>

<template>
  <div v-if="message" class="offline-banner" role="alert">{{ message }}</div>
</template>

<style scoped>
.offline-banner {
  background: var(--color-warning);
  color: #ffffff;
  text-align: center;
  padding: 6px 12px;
  font-weight: 600;
}
</style>
