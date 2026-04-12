import { reactive } from 'vue';

export type ToastVariant = 'info' | 'success' | 'error' | 'warning';

export interface Toast {
  id: number;
  variant: ToastVariant;
  message: string;
}

interface ToastState {
  toasts: Toast[];
}

const state = reactive<ToastState>({ toasts: [] });
let nextId = 1;

function push(variant: ToastVariant, message: string, ttlMs = 4000): void {
  const id = nextId++;
  state.toasts.push({ id, variant, message });
  if (ttlMs > 0) {
    setTimeout(() => dismiss(id), ttlMs);
  }
}

function dismiss(id: number): void {
  const idx = state.toasts.findIndex((t) => t.id === id);
  if (idx >= 0) state.toasts.splice(idx, 1);
}

export function useToast() {
  return {
    toasts: state.toasts,
    info: (msg: string) => push('info', msg),
    success: (msg: string) => push('success', msg),
    error: (msg: string) => push('error', msg, 6000),
    warning: (msg: string) => push('warning', msg),
    dismiss,
  };
}
