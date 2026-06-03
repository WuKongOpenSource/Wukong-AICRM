<template>
  <button
    class="wk-floating-new-chat-button"
    :class="`wk-floating-new-chat-button--${placement}`"
    type="button"
    aria-label="新建对话"
    title="新建对话"
    @click="emit('new-chat')"
  >
    <WkIcon name="new-chat" :size="22" class="wk-floating-new-chat-button__icon" />
    <span class="wk-floating-new-chat-button__label">聊天</span>
  </button>
</template>

<script setup lang="ts">
import WkIcon from '@/components/common/WkIcon.vue'

withDefaults(defineProps<{
  placement?: 'viewport' | 'menu'
}>(), {
  placement: 'viewport'
})

const emit = defineEmits<{
  (e: 'new-chat'): void
}>()
</script>

<style scoped>
.wk-floating-new-chat-button {
  display: inline-flex;
  height: 54px;
  min-width: 124px;
  align-items: center;
  justify-content: center;
  gap: 12px;
  border: 1px solid rgb(255 255 255 / 0.12);
  border-radius: 9999px;
  background: #252525;
  color: #fff;
  box-shadow:
    0 18px 42px rgb(15 23 42 / 0.24),
    0 2px 8px rgb(15 23 42 / 0.18);
  cursor: pointer;
  padding: 0 22px;
  transition:
    background-color 160ms ease,
    box-shadow 160ms ease,
    transform 160ms ease;
}

.wk-floating-new-chat-button--viewport {
  position: fixed;
  right: max(24px, calc(18px + env(safe-area-inset-right)));
  bottom: max(24px, calc(18px + env(safe-area-inset-bottom)));
  z-index: 120;
}

.wk-floating-new-chat-button--menu {
  position: absolute;
  right: max(24px, calc(18px + env(safe-area-inset-right)));
  bottom: max(64px, calc(58px + env(safe-area-inset-bottom)));
  z-index: 20;
  box-shadow:
    0 10px 24px rgb(15 23 42 / 0.18),
    0 0 10px 2px rgb(15 23 42 / 0.18);
}

.wk-floating-new-chat-button:hover {
  background: #1f1f1f;
  box-shadow:
    0 20px 48px rgb(15 23 42 / 0.28),
    0 2px 10px rgb(15 23 42 / 0.2);
  transform: translateY(-1px);
}

.wk-floating-new-chat-button--menu:hover {
  box-shadow:
    0 12px 28px rgb(15 23 42 / 0.22),
    0 0 12px 3px rgb(15 23 42 / 0.2);
}

.wk-floating-new-chat-button:active {
  transform: translateY(1px) scale(0.98);
}

.wk-floating-new-chat-button__icon {
  flex-shrink: 0;
}

.wk-floating-new-chat-button__label {
  font-size: 18px;
  font-weight: 700;
  line-height: 1;
  letter-spacing: 0;
  white-space: nowrap;
}

@media (max-width: 640px) {
  .wk-floating-new-chat-button--viewport,
  .wk-floating-new-chat-button--menu {
    right: max(18px, calc(14px + env(safe-area-inset-right)));
  }

  .wk-floating-new-chat-button--viewport {
    bottom: max(20px, calc(16px + env(safe-area-inset-bottom)));
  }

  .wk-floating-new-chat-button--menu {
    bottom: max(60px, calc(56px + env(safe-area-inset-bottom)));
  }

  .wk-floating-new-chat-button {
    height: 52px;
    min-width: 116px;
    gap: 10px;
    padding: 0 20px;
  }

  .wk-floating-new-chat-button__label {
    font-size: 17px;
  }
}
</style>
