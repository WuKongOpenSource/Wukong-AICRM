<template>
  <el-drawer
    v-model="visible"
    direction="rtl"
    :size="'400px'"
    :with-header="false"
    :modal="false"
    :lock-scroll="false"
    body-class="schedule-detail-drawer__body"
    modal-penetrable
    class="schedule-detail-drawer"
  >
    <div v-if="schedule" class="h-full flex flex-col bg-white shadow-2xl">
      <div class="flex items-center justify-between p-6 border-b border-slate-100">
        <span class="px-3 py-1 bg-primary/10 text-primary text-xs font-bold rounded-full uppercase tracking-widest">
          日程详情
        </span>
        <div class="flex items-center gap-2">
          <button
            :disabled="deleting"
            class="size-9 flex items-center justify-center rounded-full hover:bg-red-50 text-slate-400 hover:text-red-500 transition-colors disabled:opacity-50"
            type="button"
            aria-label="删除日程"
            title="删除日程"
            @click="handleDeleteSchedule"
          >
            <span class="material-symbols-outlined text-[18px] leading-none">delete</span>
          </button>
          <button
            class="size-9 flex items-center justify-center rounded-full hover:bg-slate-100 text-slate-400 hover:text-slate-600 transition-colors"
            type="button"
            aria-label="关闭日程详情"
            title="关闭"
            @click="visible = false"
          >
            <span class="material-symbols-outlined text-xl leading-none">close</span>
          </button>
        </div>
      </div>

      <div class="flex-1 min-h-0 overflow-y-auto p-8">
        <h2 class="text-2xl font-bold text-slate-900 leading-tight mb-2">{{ schedule.title }}</h2>

        <div class="flex items-center gap-4 text-sm text-slate-500 mb-8 flex-wrap">
          <div class="flex items-center gap-1">
            <span class="material-symbols-outlined text-sm">schedule</span>
            {{ formatDateTime(schedule.startTime) }}
            <template v-if="schedule.endTime"> ~ {{ formatTime(schedule.endTime) }}</template>
          </div>
          <div v-if="schedule.location" class="flex items-center gap-1">
            <span class="material-symbols-outlined text-sm">location_on</span>
            <span class="break-words">{{ schedule.location }}</span>
          </div>
          <div v-if="schedule.typeName" class="flex items-center gap-1">
            <span class="material-symbols-outlined text-sm">label</span>
            {{ schedule.typeName }}
          </div>
        </div>

        <div class="space-y-8">
          <section v-if="schedule.customerName">
            <div class="flex items-center gap-2 mb-4">
              <span class="material-symbols-outlined text-[18px] text-slate-400">corporate_fare</span>
              <h3 class="text-xs font-bold text-slate-400 uppercase tracking-widest">关联客户</h3>
            </div>
            <div
              class="p-4 bg-white border border-slate-200 rounded-2xl flex items-center gap-3 transition-colors"
              :class="schedule.customerId ? 'cursor-pointer hover:bg-slate-50' : ''"
              @click="handleGoToCustomerDetail"
            >
              <div class="size-10 rounded-xl bg-primary/10 text-primary flex items-center justify-center font-bold">
                {{ schedule.customerName.charAt(0) }}
              </div>
              <div class="flex-1 min-w-0">
                <p class="text-sm font-bold text-slate-900 truncate">{{ schedule.customerName }}</p>
                <p v-if="schedule.contactName" class="text-xs text-slate-400 truncate">{{ schedule.contactName }}</p>
              </div>
              <span class="material-symbols-outlined ml-auto text-slate-300">chevron_right</span>
            </div>
          </section>

          <section v-if="schedule.description">
            <div class="flex items-center gap-2 mb-4">
              <span class="material-symbols-outlined text-[18px] text-slate-400">notes</span>
              <h3 class="text-xs font-bold text-slate-400 uppercase tracking-widest">备注说明</h3>
            </div>
            <div class="p-5 bg-slate-50 rounded-2xl border border-slate-100">
              <p class="text-sm text-slate-600 whitespace-pre-wrap leading-relaxed">{{ schedule.description }}</p>
            </div>
          </section>
        </div>
      </div>

      <div class="p-6 border-t border-slate-100 bg-white">
        <button
          :disabled="deleting"
          class="w-full py-3 bg-red-50 text-red-600 rounded-xl text-sm font-bold hover:bg-red-100 transition-colors disabled:opacity-50"
          @click="handleDeleteSchedule"
        >
          {{ deleting ? '删除中...' : '删除日程' }}
        </button>
      </div>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteSchedule, type ScheduleVO } from '@/api/schedule'

const props = defineProps<{
  modelValue: boolean
  schedule: ScheduleVO | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'deleted', scheduleId: string): void
}>()

const router = useRouter()
const deleting = ref(false)

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
})

function formatTime(dateStr: string): string {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

function formatDateTime(dateStr: string): string {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

async function handleGoToCustomerDetail() {
  if (!props.schedule?.customerId) return
  visible.value = false
  await router.push(`/customer/${props.schedule.customerId}`)
}

async function handleDeleteSchedule() {
  if (!props.schedule || deleting.value) return

  try {
    await ElMessageBox.confirm('确定删除该日程？', '提示', { type: 'warning' })
    deleting.value = true
    await deleteSchedule(props.schedule.scheduleId)
    ElMessage.success('日程已删除')
    emit('deleted', props.schedule.scheduleId)
    visible.value = false
  } catch (e: any) {
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error('删除日程失败')
    }
  } finally {
    deleting.value = false
  }
}
</script>

<style>
.schedule-detail-drawer__body {
  padding: 0 !important;
}
</style>
