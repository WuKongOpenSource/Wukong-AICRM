<template>
  <el-dialog
    :model-value="modelValue"
    width="680px"
    class="external-ai-quota-exhausted-dialog"
    align-center
    :show-close="true"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <template #header>
      <div class="flex items-start gap-4 pr-8">
        <span class="flex size-12 shrink-0 items-center justify-center rounded-full bg-orange-50 text-orange-600">
          <span class="material-symbols-outlined text-[28px]">bolt</span>
        </span>
        <div class="min-w-0">
          <p class="text-xl font-semibold leading-7 text-slate-950">免费体验额度已用完</p>
          <p class="mt-1 text-sm leading-6 text-slate-500">
            继续使用 AI CRM，可选择完善手机号领取额外积分、购买套餐，或接入自己的大模型 API。
          </p>
        </div>
      </div>
    </template>

    <div class="space-y-3">
      <button
        v-if="!mobileCompleted"
        type="button"
        class="group flex w-full items-center gap-4 rounded-lg border border-blue-200 bg-blue-50/70 px-4 py-4 text-left transition hover:border-blue-300 hover:bg-blue-50"
        @click="handleCompleteMobile"
      >
        <span class="flex size-12 shrink-0 items-center justify-center rounded-lg bg-blue-100 text-blue-700">
          <span class="material-symbols-outlined text-[27px]">redeem</span>
        </span>
        <span class="min-w-0 flex-1">
          <span class="flex flex-wrap items-center gap-2 text-base font-semibold text-slate-950">
            完善手机号，额外赠送 250 积分
            <span class="rounded-full bg-blue-100 px-2 py-0.5 text-xs font-medium text-blue-700">限时福利</span>
          </span>
          <span class="mt-1 block text-sm leading-6 text-slate-600">
            完成手机号验证码验证后，系统自动发放积分，仅限未绑定手机号账号。
          </span>
        </span>
        <span class="inline-flex h-10 shrink-0 items-center rounded-md bg-blue-600 px-4 text-sm font-semibold text-white transition group-hover:bg-blue-700">
          去完善
        </span>
      </button>

      <div
        v-else
        class="flex w-full items-center gap-4 rounded-lg border border-emerald-200 bg-emerald-50/70 px-4 py-4"
      >
        <span class="flex size-12 shrink-0 items-center justify-center rounded-lg bg-emerald-100 text-emerald-700">
          <span class="material-symbols-outlined text-[27px]">task_alt</span>
        </span>
        <div class="min-w-0 flex-1">
          <p class="text-base font-semibold text-slate-950">手机号福利已领取</p>
          <p class="mt-1 text-sm leading-6 text-slate-600">当前账号已完成手机号验证，可通过购买套餐或接入自有模型继续使用。</p>
        </div>
      </div>

      <button
        type="button"
        class="group flex w-full items-center gap-4 rounded-lg border border-orange-200 bg-orange-50/70 px-4 py-4 text-left transition hover:border-orange-300 hover:bg-orange-50"
        @click="handlePurchase"
      >
        <span class="flex size-12 shrink-0 items-center justify-center rounded-lg bg-orange-100 text-orange-700">
          <span class="material-symbols-outlined text-[27px]">workspace_premium</span>
        </span>
        <span class="min-w-0 flex-1">
          <span class="flex flex-wrap items-center gap-2 text-base font-semibold text-slate-950">
            购买套餐
            <span class="rounded-full bg-orange-100 px-2 py-0.5 text-xs font-medium text-orange-700">推荐</span>
          </span>
          <span class="mt-1 block text-sm leading-6 text-slate-600">
            购买 AI 算力包，恢复全部模型与 AI 功能。
          </span>
        </span>
        <span class="inline-flex h-10 shrink-0 items-center rounded-md bg-orange-600 px-4 text-sm font-semibold text-white transition group-hover:bg-orange-700">
          去购买
        </span>
      </button>

      <button
        type="button"
        class="group flex w-full items-center gap-4 rounded-lg border border-emerald-200 bg-emerald-50/70 px-4 py-4 text-left transition hover:border-emerald-300 hover:bg-emerald-50 disabled:cursor-not-allowed disabled:opacity-60"
        :disabled="!canConfigure"
        @click="handleConfigure"
      >
        <span class="flex size-12 shrink-0 items-center justify-center rounded-lg bg-emerald-100 text-emerald-700">
          <span class="material-symbols-outlined text-[27px]">construction</span>
        </span>
        <span class="min-w-0 flex-1">
          <span class="text-base font-semibold text-slate-950">配置自定义大模型</span>
          <span class="mt-1 block text-sm leading-6 text-slate-600">
            支持 OpenAI、DeepSeek、Qwen、Kimi 等兼容 API。
          </span>
        </span>
        <span class="inline-flex h-10 shrink-0 items-center rounded-md bg-emerald-700 px-4 text-sm font-semibold text-white transition group-hover:bg-emerald-800">
          去配置
        </span>
      </button>

      <p class="border-t border-slate-100 pt-3 text-center text-xs leading-5 text-slate-500">
        积分可用于继续体验 AI 对话，赠送机会每个账号仅限一次。
      </p>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
const props = withDefaults(defineProps<{
  modelValue: boolean
  mobileCompleted?: boolean
  canConfigure?: boolean
}>(), {
  mobileCompleted: false,
  canConfigure: true
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'completeMobile'): void
  (e: 'purchase'): void
  (e: 'configure'): void
}>()

function handleCompleteMobile() {
  if (props.mobileCompleted) return
  emit('completeMobile')
}

function handlePurchase() {
  emit('purchase')
}

function handleConfigure() {
  if (!props.canConfigure) return
  emit('configure')
}
</script>
