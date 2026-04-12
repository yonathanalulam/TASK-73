<script setup lang="ts">
import { useRouter } from 'vue-router';
const router = useRouter();
</script>

<template>
  <div class="wrap">
    <h1>404</h1>
    <p>The page you were looking for could not be found.</p>
    <button class="primary" @click="router.push('/')">Go home</button>
  </div>
</template>

<style scoped>
.wrap {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  text-align: center;
  padding: 48px;
  gap: 8px;
}
</style>
