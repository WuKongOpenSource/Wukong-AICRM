<template>
  <div class="flex h-full flex-col gap-5 bg-slate-50 px-4 py-5 md:px-8">
    <div class="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
      <div class="min-w-0">
        <h1 class="text-xl font-bold text-slate-900">通讯录</h1>
        <p class="mt-1 text-sm text-slate-500">共 {{ total }} 人</p>
      </div>

      <div class="flex flex-col gap-3 sm:flex-row sm:items-center">
        <div class="relative">
          <span class="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-slate-400">search</span>
          <input
            v-model="keyword"
            type="text"
            placeholder="搜索姓名、部门、职位、手机或邮箱"
            class="h-10 w-full rounded-lg border border-slate-200 bg-white pl-10 pr-4 text-sm outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/20 sm:w-80"
            @input="debouncedLoadEmployees"
            @keydown.enter="loadEmployees"
          />
        </div>
        <select
          v-model="employeeStatus"
          class="h-10 rounded-lg border border-slate-200 bg-white px-3 text-sm text-slate-600 outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/20"
          @change="loadEmployees"
        >
          <option value="">全部状态</option>
          <option value="active">在职</option>
          <option value="resigned">未激活</option>
          <option value="disabled">停用</option>
        </select>
      </div>
    </div>

    <div class="min-h-0 flex-1 overflow-hidden rounded-lg border border-slate-200 bg-white" v-loading="loading">
      <el-table
        v-if="!isMobile"
        :data="employees"
        height="100%"
        row-key="userId"
        table-layout="fixed"
        empty-text="暂无员工数据"
        @row-click="openEmployee"
      >
        <el-table-column label="员工" min-width="240">
          <template #default="{ row }">
            <div class="flex min-w-0 items-center gap-3">
              <div class="flex size-9 shrink-0 items-center justify-center overflow-hidden rounded-lg border border-slate-200 bg-white">
                <img v-if="row.imgUrl" :src="row.imgUrl" class="size-full object-cover" alt="avatar" />
                <span v-else class="flex size-full items-center justify-center bg-primary/10 text-sm font-bold text-primary">
                  {{ employeeInitial(row) }}
                </span>
              </div>
              <div class="min-w-0">
                <p class="truncate text-sm font-semibold text-slate-900">{{ employeeName(row) }}</p>
                <p class="truncate text-xs text-slate-400">{{ row.email || '-' }}</p>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="部门" min-width="150">
          <template #default="{ row }">
            <span class="block truncate text-sm text-slate-600">{{ row.deptName || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="职位" min-width="150">
          <template #default="{ row }">
            <span class="block truncate text-sm text-slate-600">{{ row.post || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="手机号" min-width="150">
          <template #default="{ row }">
            <span class="block truncate font-mono text-sm text-slate-600">{{ row.mobile || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <span class="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium" :class="employeeStatusClass(row)">
              <span class="mr-1.5 size-1.5 rounded-full" :class="employeeStatusDotClass(row)"></span>
              {{ employeeStatusLabel(row) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="最近任务" min-width="160">
          <template #default="{ row }">
            <span class="text-sm text-slate-500">{{ formatDateTime(row.recentTaskTime) }}</span>
          </template>
        </el-table-column>
      </el-table>

      <div v-else class="h-full overflow-y-auto px-3 py-3">
        <div v-if="employees.length === 0" class="py-16 text-center text-slate-400">
          <span class="material-symbols-outlined text-5xl">contacts</span>
          <p class="mt-3 text-sm">{{ keyword.trim() ? '未找到匹配员工' : '暂无员工数据' }}</p>
        </div>
        <div v-else class="flex flex-col gap-3">
          <button
            v-for="employee in employees"
            :key="employee.userId"
            type="button"
            class="w-full rounded-lg border border-slate-200 bg-white px-4 py-3 text-left transition active:bg-slate-100"
            @click="openEmployee(employee)"
          >
            <div class="flex items-start gap-3">
              <div class="flex size-10 shrink-0 items-center justify-center overflow-hidden rounded-lg border border-slate-200 bg-white">
                <img v-if="employee.imgUrl" :src="employee.imgUrl" class="size-full object-cover" alt="avatar" />
                <span v-else class="flex size-full items-center justify-center bg-primary/10 text-sm font-bold text-primary">
                  {{ employeeInitial(employee) }}
                </span>
              </div>
              <div class="min-w-0 flex-1">
                <div class="flex items-start gap-2">
                  <div class="min-w-0 flex-1">
                    <div class="truncate text-sm font-bold text-slate-900">{{ employeeName(employee) }}</div>
                    <p class="mt-0.5 truncate text-xs text-slate-400">{{ employee.post || '员工' }} · {{ employee.deptName || '-' }}</p>
                  </div>
                  <span class="material-symbols-outlined text-base leading-none text-slate-300">chevron_right</span>
                </div>
                <div class="mt-3 grid grid-cols-2 gap-3">
                  <div class="min-w-0">
                    <div class="text-[11px] font-bold text-slate-400">手机</div>
                    <div class="truncate text-sm text-slate-600">{{ employee.mobile || '-' }}</div>
                  </div>
                  <div class="min-w-0">
                    <div class="text-[11px] font-bold text-slate-400">状态</div>
                    <span class="mt-1 inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium" :class="employeeStatusClass(employee)">
                      {{ employeeStatusLabel(employee) }}
                    </span>
                  </div>
                </div>
              </div>
            </div>
          </button>
        </div>
      </div>
    </div>

    <div v-if="total > 0" class="flex items-center justify-between text-sm text-slate-500">
      <span>第 {{ page }} / {{ totalPages }} 页</span>
      <div class="flex items-center gap-2">
        <button class="rounded-lg border border-slate-200 bg-white px-3 py-2 disabled:opacity-40" :disabled="page <= 1" @click="changePage(page - 1)">上一页</button>
        <button class="rounded-lg border border-slate-200 bg-white px-3 py-2 disabled:opacity-40" :disabled="page >= totalPages" @click="changePage(page + 1)">下一页</button>
      </div>
    </div>

    <el-drawer v-model="detailVisible" :size="isMobile ? '100%' : '420px'" title="员工详情" append-to-body>
      <div v-loading="detailLoading" class="space-y-5">
        <template v-if="selectedDetail">
          <div class="flex items-center gap-3">
            <div class="flex size-12 shrink-0 items-center justify-center overflow-hidden rounded-xl border border-slate-200 bg-white">
              <img v-if="selectedDetail.imgUrl" :src="selectedDetail.imgUrl" class="size-full object-cover" alt="avatar" />
              <span v-else class="flex size-full items-center justify-center bg-primary/10 text-base font-bold text-primary">
                {{ employeeInitial(selectedDetail) }}
              </span>
            </div>
            <div class="min-w-0">
              <h2 class="truncate text-lg font-bold text-slate-900">{{ employeeName(selectedDetail) }}</h2>
              <p class="truncate text-sm text-slate-500">{{ selectedDetail.post || '员工' }} · {{ selectedDetail.deptName || '-' }}</p>
            </div>
          </div>

          <div class="grid grid-cols-1 gap-3 text-sm">
            <div v-for="item in detailRows" :key="item.label" class="rounded-lg bg-slate-50 px-3 py-2">
              <div class="text-xs font-medium text-slate-400">{{ item.label }}</div>
              <div class="mt-1 break-words text-sm font-medium text-slate-700">{{ item.value || '-' }}</div>
            </div>
          </div>

          <section v-if="selectedDetail.recentRecords?.length" class="space-y-2">
            <h3 class="text-sm font-bold text-slate-900">最近记录</h3>
            <div class="divide-y divide-slate-100 rounded-lg border border-slate-200">
              <div v-for="record in selectedDetail.recentRecords" :key="`${record.type}-${record.title}-${record.recordTime}`" class="px-3 py-3">
                <div class="flex items-center justify-between gap-3">
                  <p class="truncate text-sm font-medium text-slate-800">{{ record.title || '-' }}</p>
                  <span class="shrink-0 text-xs text-slate-400">{{ formatDateTime(record.recordTime) }}</span>
                </div>
                <p v-if="record.description" class="mt-1 line-clamp-2 text-xs leading-5 text-slate-500">{{ record.description }}</p>
              </div>
            </div>
          </section>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getAddressBookDetail, queryAddressBook } from '@/api/addressBook'
import { useResponsive } from '@/composables/useResponsive'
import { isRequestErrorHandled } from '@/utils/requestError'
import type { AddressBookDetail, AddressBookEmployee } from '@/types/addressBook'

const { isMobile } = useResponsive()

const employees = ref<AddressBookEmployee[]>([])
const loading = ref(false)
const keyword = ref('')
const employeeStatus = ref('')
const page = ref(1)
const limit = ref(20)
const total = ref(0)
const detailVisible = ref(false)
const detailLoading = ref(false)
const selectedDetail = ref<AddressBookDetail | null>(null)
let searchTimer: ReturnType<typeof setTimeout> | null = null

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / limit.value)))
const detailRows = computed(() => {
  const detail = selectedDetail.value
  if (!detail) return []
  return [
    { label: '手机', value: detail.mobile || '' },
    { label: '邮箱', value: detail.email || '' },
    { label: '直属上级', value: detail.parentName || '' },
    { label: '状态', value: employeeStatusLabel(detail) }
  ]
})

onMounted(() => {
  void loadEmployees()
})

function debouncedLoadEmployees() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    page.value = 1
    void loadEmployees()
  }, 260)
}

async function loadEmployees() {
  loading.value = true
  try {
    const result = await queryAddressBook({
      keyword: keyword.value.trim() || undefined,
      employeeStatus: employeeStatus.value || undefined,
      page: page.value,
      limit: limit.value
    })
    employees.value = result.list || []
    total.value = Number(result.totalRow || 0)
  } catch (error) {
    if (!isRequestErrorHandled(error)) {
      ElMessage.warning('通讯录加载失败')
    }
    employees.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function changePage(nextPage: number) {
  if (nextPage < 1 || nextPage > totalPages.value || nextPage === page.value) return
  page.value = nextPage
  void loadEmployees()
}

async function openEmployee(employee: AddressBookEmployee) {
  detailVisible.value = true
  detailLoading.value = true
  selectedDetail.value = null
  try {
    selectedDetail.value = await getAddressBookDetail(employee.userId)
  } catch (error) {
    if (!isRequestErrorHandled(error)) {
      ElMessage.warning('员工详情加载失败')
    }
  } finally {
    detailLoading.value = false
  }
}

function employeeName(employee: AddressBookEmployee) {
  return employee.realname || '未命名员工'
}

function employeeInitial(employee: AddressBookEmployee) {
  return employeeName(employee).charAt(0) || '?'
}

function normalizeStatus(status?: string) {
  return status === 'resigned' || status === 'disabled' ? status : 'active'
}

function employeeStatusLabel(employee: AddressBookEmployee) {
  if (employee.employeeStatusName) return employee.employeeStatusName
  const status = normalizeStatus(employee.employeeStatus)
  if (status === 'resigned') return '未激活'
  if (status === 'disabled') return '停用'
  return '在职'
}

function employeeStatusClass(employee: AddressBookEmployee) {
  const status = normalizeStatus(employee.employeeStatus)
  if (status === 'resigned') return 'bg-slate-100 text-slate-600'
  if (status === 'disabled') return 'bg-amber-50 text-amber-700'
  return 'bg-emerald-50 text-emerald-700'
}

function employeeStatusDotClass(employee: AddressBookEmployee) {
  const status = normalizeStatus(employee.employeeStatus)
  if (status === 'resigned') return 'bg-slate-400'
  if (status === 'disabled') return 'bg-amber-500'
  return 'bg-emerald-500'
}

function formatDateTime(value?: string) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}
</script>
