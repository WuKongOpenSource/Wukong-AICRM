<template>
  <div class="min-h-0 min-w-0 flex-1 overflow-hidden">
    <div
      class="wk-card-view-scroll min-h-0 overflow-y-auto overflow-x-hidden p-4 sm:p-5"
      :style="{ maxHeight: `${scrollMaxHeight}px` }"
    >
      <div
        v-if="customers.length === 0"
        class="rounded-3xl border border-dashed border-slate-200 bg-slate-50/30 py-24 text-center"
      >
        <span class="material-symbols-outlined mb-4 block text-5xl font-light text-slate-200">search_off</span>
        <p class="text-sm text-slate-400">暂无客户数据</p>
      </div>
      <div
        v-else
        class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 2xl:grid-cols-5"
      >
        <div
          v-for="row in customers"
          :key="row.customerId"
          role="button"
          tabindex="0"
          class="group relative cursor-pointer overflow-hidden rounded-2xl border border-slate-200 bg-white p-4 shadow-sm transition-all hover:border-primary/20 hover:shadow-xl"
          @click="emit('rowClick', row)"
          @keydown.enter.prevent="emit('rowClick', row)"
        >
          <div
            class="pointer-events-none absolute -right-12 -mt-12 top-0 right-0 h-24 w-24 rounded-bl-full bg-primary/5 transition-all group-hover:scale-110 group-hover:bg-primary/10"
          />
          <div class="relative z-10">
            <div class="mb-3 flex items-start justify-between">
              <div
                class="flex size-10 items-center justify-center overflow-hidden rounded-xl border border-slate-100 bg-slate-50 transition-colors group-hover:border-primary/20"
              >
                <img
                  v-if="row.logoUrl"
                  :src="row.logoUrl"
                  alt=""
                  class="size-full object-contain p-1.5"
                />
                <span v-else class="material-symbols-outlined text-xl text-slate-400">corporate_fare</span>
              </div>
              <div class="flex flex-col items-end gap-1.5">
                <template v-if="getAiStatusMeta(row.aiStatusDetection)">
                  <span
                    class="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium"
                    :class="getAiStatusMeta(row.aiStatusDetection)?.badgeClass"
                  >
                    <span
                      class="mr-1.5 size-1.5 shrink-0 rounded-full"
                      :class="getAiStatusMeta(row.aiStatusDetection)?.dotClass"
                    />
                    {{ getAiStatusMeta(row.aiStatusDetection)?.label }}
                  </span>
                </template>
                <div
                  class="max-w-[140px] truncate rounded-md bg-slate-100 px-1.5 py-0.5 text-[9px] font-bold uppercase tracking-wider text-slate-500"
                  :title="industryLabel(row)"
                >
                  {{ industryLabel(row) || '通用行业' }}
                </div>
              </div>
            </div>
            <div class="mb-3 space-y-1">
              <h3 class="line-clamp-1 text-base font-bold text-slate-900 transition-colors group-hover:text-primary">
                {{ row.companyName || '-' }}
              </h3>
              <div class="flex flex-wrap items-center gap-1.5 text-slate-500">
                <span class="text-[11px] font-medium">{{ row.primaryContactName || '-' }}</span>
                <span class="size-1 shrink-0 rounded-full bg-slate-300" />
                <span class="font-mono text-[11px]">{{ row.primaryContactPhone || '-' }}</span>
              </div>
            </div>
            <div class="mb-4 grid grid-cols-2 gap-3">
              <div class="rounded-xl border border-slate-100 bg-slate-50 p-2 transition-colors group-hover:bg-white">
                <p class="mb-0.5 text-[9px] font-bold uppercase tracking-tight text-slate-400">预计价值</p>
                <p class="text-xs font-bold text-slate-900">
                  {{ formatCardQuotation(row.quotation) }}
                </p>
              </div>
              <div class="rounded-xl border border-slate-100 bg-slate-50 p-2 transition-colors group-hover:bg-white">
                <p class="mb-0.5 text-[9px] font-bold uppercase tracking-tight text-slate-400">当前阶段</p>
                <div class="flex min-w-0 items-center gap-1">
                  <div class="size-1 shrink-0 animate-pulse rounded-full bg-primary" />
                  <p class="truncate text-xs font-bold text-primary">
                    {{ stageLabel(row) }}
                  </p>
                </div>
              </div>
            </div>
            <div
              v-if="row.aiInsight"
              class="relative mb-4 rounded-xl border border-primary/10 bg-primary/5 p-3 transition-colors group-hover:bg-primary/10"
            >
              <div class="mb-1.5 flex items-center gap-1.5">
                <span class="material-symbols-outlined text-[14px] text-primary">auto_awesome</span>
                <span class="text-[9px] font-bold uppercase tracking-widest text-primary">AI 洞察</span>
              </div>
              <p class="line-clamp-2 text-[10px] leading-relaxed text-slate-600">
                {{ aiInsightPreview(row.aiInsight) }}
              </p>
            </div>
            <div class="flex items-center justify-between border-t border-slate-100 pt-3" data-row-action="true" @click.stop>
              <div class="flex min-w-0 items-center gap-2">
                <div
                  class="flex size-7 shrink-0 items-center justify-center overflow-hidden rounded-full border-2 border-white bg-slate-100 text-[10px] font-bold text-slate-500 shadow-sm"
                >
                  {{ row.ownerName?.charAt(0) || '?' }}
                </div>
                <div class="min-w-0 flex flex-col">
                  <span class="text-[9px] font-medium text-slate-400">负责人</span>
                  <span class="truncate text-[11px] font-bold text-slate-700">{{ row.ownerName || '-' }}</span>
                </div>
              </div>
              <button
                type="button"
                class="flex size-8 shrink-0 items-center justify-center rounded-lg bg-primary text-white shadow-lg shadow-primary/20 transition-all hover:scale-105 active:scale-95"
                title="AI 跟进"
                @click="emit('aiFollowUp', row)"
              >
                <WkIcon name="ai" class="text-base" />
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import WkIcon from '@/components/common/WkIcon.vue'
import type { CustomerListVO } from '@/types/customer'
import { compactCustomerAiInsight, getCustomerAiStatusMeta } from '@/utils/customerAi'
import { formatCardQuotation } from '@/utils/customerListViewUi'

defineProps<{
  customers: CustomerListVO[]
  scrollMaxHeight: number
  industryLabel: (row: CustomerListVO) => string
  stageLabel: (row: CustomerListVO) => string
}>()

const emit = defineEmits<{
  rowClick: [row: CustomerListVO]
  aiFollowUp: [row: CustomerListVO]
}>()

function getAiStatusMeta(value: string | undefined | null) {
  return getCustomerAiStatusMeta(value)
}

function aiInsightPreview(value: string | undefined | null): string {
  return compactCustomerAiInsight(value) || '-'
}
</script>

<style scoped>
.wk-card-view-scroll {
  -webkit-overflow-scrolling: touch;
  scrollbar-width: thin;
}

.wk-card-view-scroll::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

.wk-card-view-scroll::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: rgb(148 163 184 / 0.55);
}
</style>
