<template>
  <el-dialog
    :model-value="modelValue"
    width="860px"
    class="external-ai-purchase-dialog"
    :close-on-click-modal="!creatingOrder && !refreshingOrder"
    @update:model-value="handleVisibleChange"
  >
    <template #header>
      <div class="flex items-center gap-3">
        <span class="flex size-10 items-center justify-center overflow-hidden rounded-xl bg-white shadow-sm ring-1 ring-slate-100">
          <img src="/logo.png" alt="" class="size-full object-cover" />
        </span>
        <div>
          <p class="text-base font-semibold text-slate-950">悟空云 AI 额度</p>
          <p class="mt-1 text-xs text-slate-500">购买后立即进入远端额度账本</p>
        </div>
      </div>
    </template>

    <div class="grid gap-4 lg:grid-cols-[minmax(0,1fr)_320px]">
      <section class="space-y-4">
        <div class="grid gap-3 sm:grid-cols-3">
          <div class="rounded-lg border border-slate-200 bg-slate-50 px-4 py-3">
            <p class="text-xs font-medium text-slate-500">可用额度</p>
            <p class="mt-2 text-xl font-semibold tabular-nums text-slate-950">{{ formatCredit(usage?.creditRemaining ?? options?.creditRemaining ?? 0) }}</p>
          </div>
          <div class="rounded-lg border border-slate-200 bg-white px-4 py-3">
            <p class="text-xs font-medium text-slate-500">购买剩余</p>
            <p class="mt-2 text-xl font-semibold tabular-nums text-slate-950">{{ formatCredit(usage?.purchasedCreditRemaining ?? options?.purchasedCreditRemaining ?? 0) }}</p>
          </div>
          <div class="rounded-lg border border-slate-200 bg-white px-4 py-3">
            <p class="text-xs font-medium text-slate-500">预留中</p>
            <p class="mt-2 text-xl font-semibold tabular-nums text-slate-950">{{ formatCredit(usage?.reservedCreditUsed ?? options?.reservedCreditUsed ?? 0) }}</p>
          </div>
        </div>

        <div class="rounded-lg border border-slate-200 bg-white p-4">
          <div class="mb-3 flex items-center justify-between gap-3">
            <p class="text-sm font-semibold text-slate-900">额度套餐</p>
            <el-button size="small" plain :loading="loading" @click="loadBillingData">刷新</el-button>
          </div>
          <div class="grid gap-3 sm:grid-cols-2">
            <button
              v-for="plan in plans"
              :key="plan.id"
              type="button"
              class="rounded-lg border px-4 py-3 text-left transition-all"
              :class="selectedPlanId === plan.id
                ? 'border-primary bg-primary/5 text-primary shadow-sm'
                : 'border-slate-200 bg-white text-slate-700 hover:border-primary/50'"
              @click="selectedPlanId = plan.id"
            >
              <span class="block text-sm font-semibold">{{ plan.name }}</span>
              <span class="mt-1 block text-xs text-slate-500">{{ formatCredit(plan.creditAmount) }} 积分</span>
              <span class="mt-3 block text-lg font-semibold tabular-nums text-slate-950">{{ priceText(plan.priceFen) }}</span>
            </button>
          </div>
        </div>

        <div class="rounded-lg border border-slate-200 bg-white p-4">
          <p class="mb-3 text-sm font-semibold text-slate-900">支付方式</p>
          <div class="flex flex-wrap gap-2">
            <button
              v-for="channel in channels"
              :key="channel.code"
              type="button"
              class="rounded-lg border px-4 py-2 text-sm font-medium transition-all disabled:cursor-not-allowed disabled:opacity-50"
              :class="selectedChannel === channel.code
                ? 'border-primary bg-primary text-white'
                : 'border-slate-200 bg-white text-slate-700 hover:border-primary/50'"
              :disabled="!channel.enabled"
              :title="channel.unavailableReason || channel.label"
              @click="selectedChannel = channel.code"
            >
              {{ channel.label }}
            </button>
          </div>
        </div>
      </section>

      <aside class="rounded-lg border border-slate-200 bg-slate-50 p-4">
        <div v-if="currentOrder" class="space-y-4">
          <div class="flex items-center justify-between gap-3">
            <div>
              <p class="text-sm font-semibold text-slate-900">{{ currentOrder.planName }}</p>
              <p class="mt-1 text-xs text-slate-500">{{ currentOrder.amountDisplay }}</p>
            </div>
            <el-tag :type="currentOrder.status === 'PAID' ? 'success' : currentOrder.status === 'PENDING' ? 'warning' : 'info'">
              {{ statusText(currentOrder.status) }}
            </el-tag>
          </div>

          <div v-if="currentOrder.paymentMode === 'qrcode' && currentOrder.qrCodeImage" class="rounded-lg bg-white p-4 text-center shadow-inner">
            <img :src="currentOrder.qrCodeImage" alt="支付二维码" class="mx-auto size-56" />
          </div>
          <iframe
            v-else-if="currentOrder.paymentMode === 'page' && currentOrder.paymentFormHtml"
            class="h-72 w-full rounded-lg border border-slate-200 bg-white"
            sandbox="allow-forms allow-scripts allow-same-origin"
            :srcdoc="currentOrder.paymentFormHtml"
          />
          <div v-else class="flex h-56 items-center justify-center rounded-lg border border-dashed border-slate-300 bg-white text-sm text-slate-400">
            支付载荷生成中
          </div>

          <div class="grid grid-cols-2 gap-2">
            <el-button :loading="refreshingOrder" @click="refreshCurrentOrder">刷新状态</el-button>
            <el-button type="primary" :loading="creatingOrder" @click="createOrder">重新下单</el-button>
          </div>
        </div>

        <div v-else class="flex min-h-[360px] flex-col justify-between">
          <div>
            <p class="text-sm font-semibold text-slate-900">待生成订单</p>
            <p class="mt-2 text-sm leading-6 text-slate-500">选择套餐和支付方式后创建支付单。</p>
          </div>
          <el-button type="primary" size="large" :loading="creatingOrder" :disabled="!canCreateOrder" @click="createOrder">
            生成支付单
          </el-button>
        </div>
      </aside>
    </div>

    <div v-if="recentOrders.length" class="mt-4 rounded-lg border border-slate-200 bg-white">
      <div class="border-b border-slate-100 px-4 py-3 text-sm font-semibold text-slate-900">最近订单</div>
      <div class="divide-y divide-slate-100">
        <div v-for="order in recentOrders.slice(0, 4)" :key="order.orderNo" class="flex items-center justify-between gap-3 px-4 py-3 text-sm">
          <div class="min-w-0">
            <p class="truncate font-medium text-slate-800">{{ order.planName }}</p>
            <p class="mt-1 text-xs text-slate-500">{{ order.orderNo }}</p>
          </div>
          <div class="text-right">
            <p class="font-semibold tabular-nums text-slate-900">{{ order.amountDisplay }}</p>
            <p class="mt-1 text-xs text-slate-500">{{ statusText(order.status) }}</p>
          </div>
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createExternalAiPurchaseOrder,
  getExternalAiPurchaseOrder,
  getExternalAiPurchaseOptions,
  getExternalAiUsage,
  listExternalAiPurchaseOrders
} from '@/api/systemConfig'
import type {
  ExternalAiPaymentChannel,
  ExternalAiPurchaseOptions,
  ExternalAiPurchaseOrder,
  ExternalAiPurchaseOrderStatus,
  ExternalAiUsage
} from '@/types/systemConfig'

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'paid'): void
}>()

const loading = ref(false)
const creatingOrder = ref(false)
const refreshingOrder = ref(false)
const options = ref<ExternalAiPurchaseOptions | null>(null)
const usage = ref<ExternalAiUsage | null>(null)
const currentOrder = ref<ExternalAiPurchaseOrder | null>(null)
const recentOrders = ref<ExternalAiPurchaseOrder[]>([])
const selectedPlanId = ref('')
const selectedChannel = ref<ExternalAiPaymentChannel>('wechat')
let pollingTimer: number | null = null

const plans = computed(() => options.value?.plans || [])
const channels = computed(() => options.value?.channels || [])
const canCreateOrder = computed(() => Boolean(
  options.value?.enabled &&
  selectedPlanId.value &&
  channels.value.some((channel) => channel.code === selectedChannel.value && channel.enabled)
))

watch(() => props.modelValue, (visible) => {
  if (visible) {
    loadBillingData()
  } else {
    stopPolling()
  }
})

onBeforeUnmount(stopPolling)

function handleVisibleChange(value: boolean) {
  emit('update:modelValue', value)
}

async function loadBillingData() {
  loading.value = true
  try {
    const [nextOptions, nextUsage, nextOrders] = await Promise.all([
      getExternalAiPurchaseOptions(),
      getExternalAiUsage(20),
      listExternalAiPurchaseOrders(10)
    ])
    options.value = nextOptions
    usage.value = nextUsage
    recentOrders.value = nextOrders
    if (!selectedPlanId.value && nextOptions.plans.length) {
      selectedPlanId.value = nextOptions.plans[0].id
    }
    const enabledChannel = nextOptions.channels.find((channel) => channel.enabled)
    if (enabledChannel) {
      selectedChannel.value = enabledChannel.code
    }
  } catch {
    // handled by interceptor
  } finally {
    loading.value = false
  }
}

async function createOrder() {
  if (!canCreateOrder.value) return
  creatingOrder.value = true
  try {
    currentOrder.value = await createExternalAiPurchaseOrder({
      planId: selectedPlanId.value,
      paymentChannel: selectedChannel.value
    })
    startPolling()
  } catch {
    // handled by interceptor
  } finally {
    creatingOrder.value = false
  }
}

async function refreshCurrentOrder() {
  if (!currentOrder.value?.orderNo) return
  refreshingOrder.value = true
  try {
    const nextOrder = await getExternalAiPurchaseOrder(currentOrder.value.orderNo)
    currentOrder.value = nextOrder
    if (nextOrder.status === 'PAID') {
      stopPolling()
      ElMessage.success('支付成功，额度已到账')
      await loadBillingData()
      emit('paid')
    }
  } catch {
    // handled by interceptor
  } finally {
    refreshingOrder.value = false
  }
}

function startPolling() {
  stopPolling()
  pollingTimer = window.setInterval(() => {
    if (currentOrder.value?.status === 'PENDING') {
      refreshCurrentOrder()
    }
  }, 3000)
}

function stopPolling() {
  if (pollingTimer) {
    window.clearInterval(pollingTimer)
    pollingTimer = null
  }
}

function formatCredit(value: number) {
  if (value >= 10000) {
    return `${(value / 10000).toFixed(value % 10000 === 0 ? 0 : 1)}万`
  }
  return `${value}`
}

function priceText(priceFen: number) {
  return `¥${(priceFen / 100).toFixed(2)}`
}

function statusText(status: ExternalAiPurchaseOrderStatus) {
  const map: Record<ExternalAiPurchaseOrderStatus, string> = {
    PENDING: '待支付',
    PAID: '已支付',
    FAILED: '失败',
    CLOSED: '已关闭',
    EXPIRED: '已过期'
  }
  return map[status] || status
}
</script>

<style scoped>
.external-ai-purchase-dialog :deep(.el-dialog__body) {
  padding-top: 8px;
}
</style>
