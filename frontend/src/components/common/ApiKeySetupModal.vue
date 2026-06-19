<template>
  <Teleport to="body">
    <Transition name="fade">
      <div
        v-if="modelValue"
        class="fixed inset-0 z-[180] bg-slate-900/50 backdrop-blur-sm"
        @click="handleClose"
      />
    </Transition>

    <Transition name="scale-fade">
      <div
        v-if="modelValue"
        class="fixed inset-0 z-[181] flex items-center justify-center p-5 sm:p-8"
      >
        <div
          class="flex w-full max-w-md flex-col overflow-hidden rounded-[2rem] border border-slate-100 bg-white shadow-2xl shadow-slate-900/15"
          @click.stop
        >
          <div class="p-6 sm:p-8">
            <div class="mx-auto mb-6 flex size-16 items-center justify-center overflow-hidden rounded-2xl bg-white shadow-sm ring-1 ring-slate-100">
              <img src="/logo.png" alt="" class="size-full object-cover" />
            </div>

            <h3 class="mb-3 text-center text-xl font-bold text-slate-900">
              悟空云 AI
            </h3>
            <p class="mx-auto max-w-sm text-center text-sm leading-6 text-slate-500">
              单机版已内置悟空云 AI，系统会自动注册并维护服务密钥，不支持手动填写 API Key。
            </p>

            <div class="mt-6 rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4">
              <div class="flex items-center justify-between gap-3">
                <div class="min-w-0">
                  <p class="truncate text-sm font-semibold text-slate-900">{{ providerLabel }}</p>
                  <p class="mt-1 text-xs text-slate-500">{{ providerStatus }}</p>
                </div>
                <span class="shrink-0 rounded-full bg-primary/10 px-3 py-1 text-xs font-semibold text-primary">
                  系统托管
                </span>
              </div>
              <p class="mt-3 text-xs leading-5 text-slate-500">
                可完善手机号领取更多额度，也可以购买额度继续使用。
              </p>
            </div>

            <div class="mt-6 flex gap-3">
              <button
                type="button"
                class="flex-1 rounded-2xl bg-slate-100 px-6 py-4 text-sm font-bold text-slate-600 transition-all hover:bg-slate-200 disabled:cursor-not-allowed disabled:opacity-50"
                :disabled="loading"
                @click="handleClose"
              >
                暂不处理
              </button>
              <button
                type="button"
                class="flex flex-1 items-center justify-center rounded-2xl bg-primary px-6 py-4 text-sm font-bold text-white shadow-lg shadow-primary/20 transition-all hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-50"
                :disabled="loading"
                @click="handleManageQuota"
              >
                管理/购买额度
              </button>
            </div>
          </div>

          <div class="shrink-0 border-t border-slate-100 bg-slate-50 p-4">
            <p class="text-center text-[10px] font-bold uppercase tracking-[0.32em] text-slate-400">
              悟空AI 安全加密传输
            </p>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { AiConfigUpdateBO, AiProviderPreset } from '@/types/systemConfig'

const props = withDefaults(defineProps<{
  modelValue: boolean
  loading?: boolean
  providerOptions?: AiProviderPreset[]
  initialConfig?: Partial<AiConfigUpdateBO> | null
}>(), {
  loading: false,
  providerOptions: () => [],
  initialConfig: null
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'save', value: AiConfigUpdateBO): void
  (e: 'manageQuota'): void
}>()

const wukongPreset = computed(() => props.providerOptions.find((item) => item.value === 'wukong_external') || null)
const providerLabel = computed(() => wukongPreset.value?.label || '悟空云 AI')
const providerStatus = computed(() => {
  if (wukongPreset.value?.apiKeyConfigured || props.initialConfig?.apiKey) {
    return '悟空云 AI 已开通，可管理或购买更多额度。'
  }
  return '首次登录会自动注册无手机号账户，无需手动配置。'
})

function handleClose() {
  if (props.loading) return
  emit('update:modelValue', false)
}

function handleManageQuota() {
  if (props.loading) return
  emit('update:modelValue', false)
  emit('manageQuota')
}
</script>

<style scoped>
.fade-enter-active,
.fade-leave-active,
.scale-fade-enter-active,
.scale-fade-leave-active {
  transition: all 0.24s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.scale-fade-enter-from,
.scale-fade-leave-to {
  opacity: 0;
  transform: translateY(20px) scale(0.96);
}
</style>
