<script setup lang="ts">
import { ref } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { useAuthStore } from '@/stores/auth';
import { ApiException } from '@/api/client';
import { useToast } from '@/composables/useToast';

const auth = useAuthStore();
const router = useRouter();
const route = useRoute();
const toast = useToast();

const username = ref('');
const password = ref('');
const submitting = ref(false);
const error = ref<string | null>(null);

async function handleSubmit() {
  error.value = null;
  if (!username.value || !password.value) {
    error.value = 'Username and password are required.';
    return;
  }
  submitting.value = true;
  try {
    await auth.login({ username: username.value, password: password.value });
    toast.success(`Welcome back, ${auth.user?.fullName}`);
    const redirect = (route.query.redirect as string) || '/';
    await router.push(redirect);
  } catch (e) {
    if (e instanceof ApiException) {
      if (e.apiError.code === 'ACCOUNT_LOCKED') {
        error.value =
          'This account has been locked due to repeated failed sign-in attempts. ' +
          'Try again later or contact an administrator.';
      } else {
        error.value = e.apiError.message || 'Sign-in failed.';
      }
    } else {
      error.value = 'Unexpected error during sign in.';
    }
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <div class="login-wrap">
    <form class="login-card" @submit.prevent="handleSubmit" novalidate>
      <h1>DojoStay</h1>
      <p class="subtitle">Sign in to continue</p>

      <label for="username">Username</label>
      <input
        id="username"
        v-model="username"
        type="text"
        autocomplete="username"
        :disabled="submitting"
        required
      />

      <label for="password">Password</label>
      <input
        id="password"
        v-model="password"
        type="password"
        autocomplete="current-password"
        :disabled="submitting"
        required
      />

      <div v-if="error" class="error" role="alert">{{ error }}</div>

      <button class="primary" type="submit" :disabled="submitting">
        {{ submitting ? 'Signing in…' : 'Sign in' }}
      </button>

      <p class="hint">DojoStay runs on your local network. No data leaves the dojo.</p>
    </form>
  </div>
</template>

<style scoped>
.login-wrap {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}
.login-card {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  padding: 32px;
  width: 100%;
  max-width: 380px;
  box-shadow: var(--shadow);
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.login-card h1 {
  margin: 0;
  font-size: 24px;
}
.subtitle {
  margin: 0 0 16px;
  color: var(--color-text-muted);
}
.login-card label {
  margin-top: 8px;
  font-weight: 600;
  font-size: 13px;
}
.error {
  background: #ffebe9;
  color: var(--color-danger);
  border: 1px solid #ffc1ba;
  padding: 8px 10px;
  border-radius: var(--radius);
  font-size: 13px;
  margin-top: 8px;
}
.login-card button.primary {
  margin-top: 16px;
  padding: 10px;
}
.hint {
  margin: 16px 0 0;
  font-size: 12px;
  color: var(--color-text-muted);
  text-align: center;
}
</style>
