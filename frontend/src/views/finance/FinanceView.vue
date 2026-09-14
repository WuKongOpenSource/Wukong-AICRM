<template>
  <div class="finance-page h-full overflow-auto bg-background-light px-4 py-4 md:px-8 md:py-6">
    <header class="mb-6 space-y-5">
      <div class="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
        <div class="min-w-0">
          <h1 class="text-2xl font-bold tracking-tight text-slate-900">财务列表</h1>
          <p class="mt-1 text-sm text-slate-500">查看、校验和修正 AI 写入的合同、应收、回款、发票和费用记录</p>
        </div>
        <div class="flex flex-wrap items-center gap-2">
          <button v-if="canCreate" class="finance-create-button" @click="openCreateDialog">
            <span class="material-symbols-outlined text-[19px]">add</span>
            新增{{ activeMeta.label }}
          </button>
          <el-dropdown trigger="click" popper-class="wk-customer-import-export-dropdown">
            <button
              type="button"
              class="flex size-10 items-center justify-center rounded-xl border border-slate-200 bg-white text-slate-500 shadow-sm transition-all hover:border-primary/30 hover:bg-slate-50 hover:text-primary"
              title="导入导出"
            >
              <span class="material-symbols-outlined text-[22px]">more_vert</span>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item :disabled="!canCreate" @click="openImportPicker">
                  <span class="flex items-center gap-2">
                    <span class="material-symbols-outlined text-[16px]">upload</span>
                    导入{{ activeMeta.label }}
                  </span>
                </el-dropdown-item>
                <el-dropdown-item :disabled="exporting || !canExport" @click="handleExport">
                  <span class="flex items-center gap-2">
                    <span class="material-symbols-outlined text-[16px]">download</span>
                    {{ exporting ? '导出中...' : `导出${activeMeta.label}` }}
                  </span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <input ref="importInputRef" type="file" class="hidden" accept=".csv,text/csv" @change="handleImportFileChange" />
        </div>
      </div>

      <el-tabs v-model="activeTab" class="finance-tabs" @tab-change="handleTabChange">
        <el-tab-pane label="合同" name="contract" />
        <el-tab-pane label="应收" name="receivable" />
        <el-tab-pane label="回款" name="payment" />
        <el-tab-pane label="发票" name="invoice" />
        <el-tab-pane label="费用" name="expense" />
      </el-tabs>

      <div class="grid gap-3 md:grid-cols-[minmax(220px,1fr)_180px_180px]">
        <el-input
          v-model="query.keyword"
          clearable
          placeholder="搜索客户、项目或备注"
          :prefix-icon="Search"
          @keyup.enter="loadRecords"
          @clear="loadRecords"
        />
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          class="!w-full"
          @change="loadRecords"
        />
        <el-select v-model="query.status" clearable placeholder="状态" @change="loadRecords">
          <el-option v-for="item in activeMeta.statuses" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </div>
    </header>

    <main class="flex flex-col gap-6 xl:flex-row xl:items-start">
      <section class="w-full min-w-0 flex-1 space-y-6">
        <div class="flex min-h-0 min-w-0 flex-col overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm" v-loading="loading">
          <el-table
            :data="records"
            height="100%"
            table-layout="fixed"
            class="wk-customer-table"
            empty-text="暂无财务记录"
            row-key="id"
            @row-click="openDetail"
          >
          <el-table-column :label="activeMeta.primaryLabel" min-width="220" show-overflow-tooltip>
            <template #header>
              <span class="normal-case tracking-normal">{{ activeMeta.primaryLabel }}</span>
            </template>
            <template #default="{ row }">
              <button class="max-w-full truncate text-left font-medium text-slate-900 hover:text-primary" @click.stop="openDetail(row)">
                {{ primaryText(row) }}
              </button>
            </template>
          </el-table-column>
          <el-table-column label="客户/项目" min-width="190" show-overflow-tooltip>
            <template #header>
              <span class="normal-case tracking-normal">客户/项目</span>
            </template>
            <template #default="{ row }">{{ row.customerName || row.projectName || '-' }}</template>
          </el-table-column>
          <el-table-column label="金额" width="130" align="right">
            <template #header>
              <span class="normal-case tracking-normal">金额</span>
            </template>
            <template #default="{ row }">
              <span class="finance-amount" :class="amountToneClass">{{ money(row.amount) }}</span>
            </template>
          </el-table-column>
          <el-table-column v-if="activeTab === 'receivable'" label="未收" width="130" align="right">
            <template #header>
              <span class="normal-case tracking-normal">未收</span>
            </template>
            <template #default="{ row }">
              <span class="finance-amount finance-amount--risk">{{ money(row.unpaidAmount) }}</span>
            </template>
          </el-table-column>
          <el-table-column :label="activeMeta.dateLabel" width="150">
            <template #header>
              <span class="normal-case tracking-normal">{{ activeMeta.dateLabel }}</span>
            </template>
            <template #default="{ row }">{{ rowDate(row) || '-' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #header>
              <span class="normal-case tracking-normal">状态</span>
            </template>
            <template #default="{ row }">
              <el-tag size="small" :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="来源" width="90">
            <template #header>
              <span class="normal-case tracking-normal">来源</span>
            </template>
            <template #default="{ row }">
              <el-tag v-if="row.aiCreated" size="small" type="warning" effect="plain">AI</el-tag>
              <span v-else class="text-xs text-slate-400">手动</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="112" fixed="right">
            <template #header>
              <span class="normal-case tracking-normal">操作</span>
            </template>
            <template #default="{ row }">
              <div class="flex justify-end gap-1">
                <button v-if="canEdit" class="finance-icon-btn" title="编辑" @click.stop="openEditDialog(row)">
                  <span class="material-symbols-outlined text-[18px]">edit</span>
                </button>
                <button v-if="canDelete" class="finance-icon-btn text-rose-500" title="删除" @click.stop="handleDelete(row)">
                  <span class="material-symbols-outlined text-[18px]">delete</span>
                </button>
              </div>
            </template>
          </el-table-column>
          <template #empty>
            <div class="py-16 text-center">
              <div class="mx-auto mb-4 flex size-16 items-center justify-center rounded-full bg-slate-50 text-slate-200">
                <span class="material-symbols-outlined text-4xl">receipt_long</span>
              </div>
              <p class="text-sm font-medium text-slate-400">暂无财务记录</p>
            </div>
          </template>
        </el-table>

          <footer v-if="total > 0" class="wk-customer-pagination-bar flex shrink-0 items-center justify-between border-t border-slate-200 bg-slate-50/50 px-6 py-4">
            <span class="text-sm text-slate-500">共 {{ total }} 条<span class="hidden md:inline">财务数据</span></span>
            <div class="flex items-center gap-1">
              <button
                class="flex size-8 items-center justify-center rounded border border-slate-200 bg-white text-slate-500 disabled:opacity-50"
                :disabled="page <= 1"
                @click="handlePageChange(page - 1)"
              >
                <span class="material-symbols-outlined text-lg">chevron_left</span>
              </button>
              <button
                v-for="pageNum in visiblePages"
                :key="pageNum"
                class="flex size-8 items-center justify-center rounded border text-xs font-bold"
                :class="pageNum === page
                  ? 'border-primary bg-primary text-white'
                  : 'border-slate-200 bg-white text-slate-500 hover:bg-slate-50'"
                @click="handlePageChange(pageNum)"
              >{{ pageNum }}</button>
              <span v-if="totalPages > 5" class="px-1 text-xs text-slate-400">...</span>
              <button
                class="flex size-8 items-center justify-center rounded border border-slate-200 bg-white text-slate-500 disabled:opacity-50"
                :disabled="page >= totalPages"
                @click="handlePageChange(page + 1)"
              >
                <span class="material-symbols-outlined text-lg">chevron_right</span>
              </button>
            </div>
          </footer>
        </div>
      </section>

      <aside class="hidden w-[360px] shrink-0 overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm xl:flex xl:flex-col">
        <template v-if="selectedRecord">
          <div class="shrink-0 border-b border-slate-200 px-5 py-4">
            <div class="flex items-start justify-between gap-3">
              <div class="min-w-0">
                <h2 class="truncate text-base font-bold text-slate-900">{{ primaryText(selectedRecord) }}</h2>
                <p class="mt-1 text-xs text-slate-500">{{ activeMeta.label }}详情</p>
              </div>
              <button class="finance-icon-btn" title="关闭详情" @click="selectedRecord = null">
                <span class="material-symbols-outlined text-[18px]">close</span>
              </button>
            </div>
          </div>
          <div class="min-h-0 flex-1 overflow-y-auto px-5 py-4">
            <section class="space-y-3">
              <div v-for="item in detailItems" :key="item.label" class="finance-detail-row">
                <div class="text-xs text-slate-500">{{ item.label }}</div>
                <div class="mt-1 break-words text-sm font-medium text-slate-900">{{ item.value || '-' }}</div>
              </div>
              <div v-for="field in customFields" :key="field.fieldId" class="finance-detail-row">
                <div class="text-xs text-slate-500">{{ field.fieldLabel }}</div>
                <div class="mt-1 break-words text-sm font-medium text-slate-900">{{ customFieldDisplay(field, selectedRecord?.customFields?.[field.fieldName]) }}</div>
              </div>
            </section>
            <section v-if="selectedRecord.sourceText" class="mt-5 border-t border-slate-200 pt-4">
              <h3 class="text-sm font-bold text-slate-900">AI 原文</h3>
              <p class="mt-2 whitespace-pre-wrap rounded-md bg-slate-50 px-3 py-2 text-sm leading-6 text-slate-600">{{ selectedRecord.sourceText }}</p>
            </section>
            <section class="mt-5 border-t border-slate-200 pt-4">
              <h3 class="text-sm font-bold text-slate-900">关联数据</h3>
              <div class="mt-3 space-y-2 text-sm">
                <button v-if="selectedRecord.customerId" class="finance-related-link" @click="goCustomer(selectedRecord.customerId)">
                  客户：{{ selectedRecord.customerName || selectedRecord.customerId }}
                </button>
                <button v-if="selectedRecord.projectId" class="finance-related-link" @click="goProject(selectedRecord.projectId)">
                  项目：{{ selectedRecord.projectName || selectedRecord.projectId }}
                </button>
                <div v-if="!selectedRecord.customerId && !selectedRecord.projectId" class="rounded-md bg-slate-50 px-3 py-3 text-center text-slate-400">
                  暂无关联客户或项目
                </div>
              </div>
            </section>
          </div>
        </template>
        <div v-else class="flex flex-1 flex-col items-center justify-center px-8 text-center">
          <h2 class="text-base font-bold text-slate-900">选择一条记录查看详情</h2>
          <p class="mt-2 text-sm leading-6 text-slate-500">详情会展示关联客户、项目、来源原文和创建信息。</p>
        </div>
      </aside>
    </main>

    <el-drawer v-model="mobileDetailVisible" title="财务详情" size="100%" destroy-on-close>
      <div v-if="selectedRecord" class="space-y-3">
        <div v-for="item in detailItems" :key="item.label" class="finance-detail-row">
          <div class="text-xs text-slate-500">{{ item.label }}</div>
          <div class="mt-1 break-words text-sm font-medium text-slate-900">{{ item.value || '-' }}</div>
        </div>
        <div v-for="field in customFields" :key="field.fieldId" class="finance-detail-row">
          <div class="text-xs text-slate-500">{{ field.fieldLabel }}</div>
          <div class="mt-1 break-words text-sm font-medium text-slate-900">{{ customFieldDisplay(field, selectedRecord?.customFields?.[field.fieldName]) }}</div>
        </div>
      </div>
    </el-drawer>

    <Teleport to="body">
      <Transition
        enter-active-class="transition duration-200 ease-out"
        enter-from-class="opacity-0"
        enter-to-class="opacity-100"
        leave-active-class="transition duration-150 ease-in"
        leave-from-class="opacity-100"
        leave-to-class="opacity-0"
      >
        <div v-if="dialogVisible" class="fixed inset-0 z-[3600] flex items-center justify-center p-4 sm:p-6">
          <div class="absolute inset-0 bg-slate-900/60 backdrop-blur-sm" @click="dialogVisible = false"></div>
          <div class="relative flex max-h-[90vh] w-full max-w-5xl flex-col overflow-hidden rounded-[2.5rem] bg-slate-50 shadow-2xl wk-crm-el-field-scope">
            <div class="flex shrink-0 items-center justify-between border-b border-slate-200 bg-white px-6 py-4 sm:px-8 sm:py-5">
              <div class="flex min-w-0 items-center gap-3 sm:gap-4">
                <div class="flex size-10 items-center justify-center rounded-2xl text-primary sm:size-12">
                  <span class="material-symbols-outlined">{{ editingRecord ? 'edit' : 'add' }}</span>
                </div>
                <div class="min-w-0">
                  <h2 class="truncate text-lg font-bold text-slate-900 sm:text-xl">{{ dialogTitle }}</h2>
                  <p class="truncate text-xs text-slate-500">手动维护 AI 写入后的正式{{ activeMeta.label }}记录</p>
                </div>
              </div>
              <div class="flex items-center gap-2 sm:gap-3">
                <button type="button" class="whitespace-nowrap rounded-xl px-4 py-2 text-sm font-bold text-slate-600 transition-colors hover:bg-slate-100 sm:px-6 sm:py-2.5" @click="dialogVisible = false">
                  取消
                </button>
                <button
                  type="button"
                  class="flex items-center gap-2 whitespace-nowrap rounded-xl bg-primary px-5 py-2 text-sm font-bold text-white shadow-lg shadow-primary/20 transition-all hover:bg-primary/90 disabled:opacity-50 sm:px-8 sm:py-2.5"
                  :disabled="saving"
                  @click="saveRecord"
                >
                  <span v-if="saving" class="size-3 animate-spin rounded-full border-2 border-white/30 border-t-white"></span>
                  <span v-else class="material-symbols-outlined text-sm">save</span>
                  {{ editingRecord ? '保存' : `创建${activeMeta.label}` }}
                </button>
              </div>
            </div>
            <div class="min-h-0 flex-1 overflow-y-auto p-4 sm:p-6">
              <section class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm sm:p-6">
                <h3 class="mb-5 flex items-center gap-2 text-xs font-bold uppercase tracking-wider text-slate-900">
                  <span class="h-3 w-1 rounded-full bg-primary"></span>
                  基础信息
                </h3>
                <div class="grid grid-cols-1 gap-x-6 gap-y-4 md:grid-cols-2">
                  <div v-for="field in activeMeta.fields" :key="field.key" class="space-y-1.5" :class="{ 'md:col-span-2': field.type === 'textarea' || ['contractName', 'title', 'remark'].includes(field.key) }">
                    <label class="ml-1 text-xs font-bold uppercase tracking-wider text-slate-500">
                      {{ field.label }} <span v-if="field.required" class="text-red-400">*</span>
                    </label>
                    <el-input-number v-if="field.type === 'money'" v-model="form[field.key]" :min="0" :precision="2" size="large" controls-position="right" class="w-full wk-crm-el-field-input" />
                    <el-date-picker v-else-if="field.type === 'date'" v-model="form[field.key]" value-format="YYYY-MM-DD" type="date" size="large" class="w-full wk-crm-el-field-date" />
                    <el-select v-else-if="field.type === 'status'" v-model="form[field.key]" clearable size="large" class="w-full wk-crm-el-field-select">
                      <el-option v-for="item in activeMeta.statuses" :key="item.value" :label="item.label" :value="item.value" />
                    </el-select>
                    <el-select v-else-if="field.type === 'customer'" v-model="form[field.key]" filterable remote clearable reserve-keyword placeholder="搜索选择客户" size="large" class="w-full wk-crm-el-field-select" :remote-method="searchCustomers" :loading="customerLoading">
                      <el-option v-for="item in customerOptions" :key="item.value" :label="item.label" :value="item.value" />
                    </el-select>
                    <el-select v-else-if="field.type === 'project'" v-model="form[field.key]" filterable remote clearable reserve-keyword placeholder="搜索选择项目" size="large" class="w-full wk-crm-el-field-select" :remote-method="searchProjects" :loading="projectLoading">
                      <el-option v-for="item in projectOptions" :key="item.value" :label="item.label" :value="item.value" />
                    </el-select>
                    <el-select v-else-if="field.type === 'contract'" v-model="form[field.key]" filterable remote clearable reserve-keyword placeholder="搜索选择合同" size="large" class="w-full wk-crm-el-field-select" :remote-method="searchContracts" :loading="contractLoading">
                      <el-option v-for="item in contractOptions" :key="item.value" :label="item.label" :value="item.value" />
                    </el-select>
                    <el-select v-else-if="field.type === 'receivable'" v-model="form[field.key]" filterable remote clearable reserve-keyword placeholder="搜索选择应收" size="large" class="w-full wk-crm-el-field-select" :remote-method="searchReceivables" :loading="receivableLoading">
                      <el-option v-for="item in receivableOptions" :key="item.value" :label="item.label" :value="item.value" />
                    </el-select>
                    <el-input v-else v-model="form[field.key]" :type="field.type === 'textarea' ? 'textarea' : 'text'" :rows="field.type === 'textarea' ? 4 : undefined" resize="none" clearable size="large" class="w-full wk-crm-el-field-input" />
                  </div>
                </div>
              </section>
              <section v-if="customFields.length" class="mt-5 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm sm:p-6">
                <h3 class="mb-5 flex items-center gap-2 text-xs font-bold uppercase tracking-wider text-slate-900">
                  <span class="h-3 w-1 rounded-full bg-indigo-500"></span>
                  自定义字段
                </h3>
                <div class="grid grid-cols-1 gap-x-6 gap-y-4 md:grid-cols-2">
                  <div v-for="field in customFields" :key="field.fieldId" class="space-y-1.5" :class="{ 'md:col-span-2': field.fieldType === 'textarea' }">
                    <label class="ml-1 text-xs font-bold uppercase tracking-wider text-slate-500">
                      {{ field.fieldLabel }} <span v-if="field.isRequired" class="text-red-400">*</span>
                    </label>
                    <el-input v-if="field.fieldType === 'text' || field.fieldType === 'textarea'" v-model="customForm[field.fieldName]" :type="field.fieldType === 'textarea' ? 'textarea' : 'text'" :rows="field.fieldType === 'textarea' ? 4 : undefined" :placeholder="field.placeholder" resize="none" clearable size="large" class="w-full wk-crm-el-field-input" />
                    <el-input-number v-else-if="field.fieldType === 'number'" v-model="customForm[field.fieldName]" size="large" controls-position="right" class="w-full wk-crm-el-field-input" />
                    <el-date-picker v-else-if="field.fieldType === 'date' || field.fieldType === 'datetime'" v-model="customForm[field.fieldName]" :type="field.fieldType" :value-format="field.fieldType === 'date' ? 'YYYY-MM-DD' : 'YYYY-MM-DD HH:mm:ss'" size="large" class="w-full wk-crm-el-field-date" />
                    <el-select v-else-if="field.fieldType === 'select'" v-model="customForm[field.fieldName]" clearable filterable size="large" class="w-full wk-crm-el-field-select">
                      <el-option v-for="option in field.options || []" :key="option.value" :label="option.label" :value="option.value" />
                    </el-select>
                    <el-select v-else-if="field.fieldType === 'multiselect'" v-model="customForm[field.fieldName]" multiple clearable filterable size="large" class="w-full wk-crm-el-field-select">
                      <el-option v-for="option in field.options || []" :key="option.value" :label="option.label" :value="option.value" />
                    </el-select>
                    <el-switch v-else-if="field.fieldType === 'checkbox'" v-model="customForm[field.fieldName]" />
                    <el-input v-else v-model="customForm[field.fieldName]" clearable size="large" class="w-full wk-crm-el-field-input" />
                  </div>
                </div>
              </section>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { queryCustomerList } from '@/api/customer'
import { getFormFieldsByEntity } from '@/api/customField'
import {
  addFinanceContract,
  addFinanceExpense,
  addFinanceInvoice,
  addFinancePayment,
  addFinanceReceivable,
  deleteFinanceContract,
  deleteFinanceExpense,
  deleteFinanceInvoice,
  deleteFinancePayment,
  deleteFinanceReceivable,
  queryFinanceContracts,
  queryFinanceExpenses,
  queryFinanceInvoices,
  queryFinancePayments,
  queryFinanceReceivables,
  updateFinanceContract,
  updateFinanceExpense,
  updateFinanceInvoice,
  updateFinancePayment,
  updateFinanceReceivable
} from '@/api/finance'
import { queryProjectPageList } from '@/api/project'
import { useUserStore } from '@/stores/user'
import type { FinanceRecordVO, FinanceTab } from '@/types/finance'
import type { CustomField, EntityType } from '@/types/customField'
import { formatCustomFieldValue } from '@/utils/customFieldDisplay'

type FinanceListTab = Exclude<FinanceTab, 'dashboard'>
type FieldMeta = {
  key: string
  label: string
  type?: 'text' | 'money' | 'date' | 'status' | 'textarea' | 'customer' | 'project' | 'contract' | 'receivable'
  required?: boolean
}
type SelectOption = { label: string; value: string }
type TabMeta = {
  label: string
  primaryLabel: string
  dateLabel: string
  idKey: keyof FinanceRecordVO
  fields: FieldMeta[]
  statuses: Array<{ label: string; value: string }>
}

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const initialTab = ['contract', 'receivable', 'payment', 'invoice', 'expense'].includes(String(route.query.tab))
  ? String(route.query.tab) as FinanceListTab
  : 'contract'
const activeTab = ref<FinanceListTab>(initialTab)
const loading = ref(false)
const saving = ref(false)
const exporting = ref(false)
const records = ref<FinanceRecordVO[]>([])
const selectedRecord = ref<FinanceRecordVO | null>(null)
const mobileDetailVisible = ref(false)
const total = ref(0)
const page = ref(1)
const limit = ref(20)
const EXPORT_PAGE_SIZE = 100
const dateRange = ref<[string, string] | null>(null)
const query = reactive({ keyword: '', status: '' })
const dialogVisible = ref(false)
const editingRecord = ref<FinanceRecordVO | null>(null)
const importInputRef = ref<HTMLInputElement | null>(null)
const form = reactive<Record<string, any>>({})
const customForm = reactive<Record<string, any>>({})
const customFields = ref<CustomField[]>([])

const customerOptions = ref<SelectOption[]>([])
const projectOptions = ref<SelectOption[]>([])
const contractOptions = ref<SelectOption[]>([])
const receivableOptions = ref<SelectOption[]>([])
const customerLoading = ref(false)
const projectLoading = ref(false)
const contractLoading = ref(false)
const receivableLoading = ref(false)

const metas: Record<FinanceListTab, TabMeta> = {
  contract: {
    label: '合同',
    primaryLabel: '合同',
    dateLabel: '签约日期',
    idKey: 'contractId',
    statuses: [{ label: '执行中', value: 'active' }, { label: '完成', value: 'completed' }, { label: '终止', value: 'terminated' }],
    fields: [
      { key: 'contractName', label: '合同名称', required: true },
      { key: 'contractNo', label: '合同编号' },
      { key: 'customerId', label: '客户', type: 'customer' },
      { key: 'projectId', label: '项目', type: 'project' },
      { key: 'amount', label: '合同金额', type: 'money', required: true },
      { key: 'signDate', label: '签约日期', type: 'date' },
      { key: 'startDate', label: '开始日期', type: 'date' },
      { key: 'endDate', label: '结束日期', type: 'date' },
      { key: 'status', label: '状态', type: 'status' },
      { key: 'remark', label: '备注', type: 'textarea' }
    ]
  },
  receivable: {
    label: '应收',
    primaryLabel: '应收标题',
    dateLabel: '应收日期',
    idKey: 'receivableId',
    statuses: [{ label: '待收', value: 'pending' }, { label: '部分回款', value: 'partial' }, { label: '已收', value: 'paid' }, { label: '逾期', value: 'overdue' }],
    fields: [
      { key: 'title', label: '标题', required: true },
      { key: 'contractId', label: '合同', type: 'contract' },
      { key: 'customerId', label: '客户', type: 'customer' },
      { key: 'projectId', label: '项目', type: 'project' },
      { key: 'amount', label: '应收金额', type: 'money', required: true },
      { key: 'dueDate', label: '应收日期', type: 'date' },
      { key: 'status', label: '状态', type: 'status' },
      { key: 'remark', label: '备注', type: 'textarea' }
    ]
  },
  payment: {
    label: '回款',
    primaryLabel: '回款',
    dateLabel: '回款日期',
    idKey: 'paymentId',
    statuses: [],
    fields: [
      { key: 'receivableId', label: '应收', type: 'receivable' },
      { key: 'contractId', label: '合同', type: 'contract' },
      { key: 'customerId', label: '客户', type: 'customer' },
      { key: 'amount', label: '回款金额', type: 'money', required: true },
      { key: 'paymentDate', label: '回款日期', type: 'date' },
      { key: 'paymentMethod', label: '回款方式' },
      { key: 'remark', label: '备注', type: 'textarea' }
    ]
  },
  invoice: {
    label: '发票',
    primaryLabel: '发票抬头',
    dateLabel: '开票日期',
    idKey: 'invoiceId',
    statuses: [{ label: '已开票', value: 'issued' }, { label: '已作废', value: 'voided' }],
    fields: [
      { key: 'title', label: '发票抬头', required: true },
      { key: 'invoiceNo', label: '发票号' },
      { key: 'taxNo', label: '税号' },
      { key: 'contractId', label: '合同', type: 'contract' },
      { key: 'receivableId', label: '应收', type: 'receivable' },
      { key: 'customerId', label: '客户', type: 'customer' },
      { key: 'amount', label: '开票金额', type: 'money', required: true },
      { key: 'invoiceDate', label: '开票日期', type: 'date' },
      { key: 'status', label: '状态', type: 'status' },
      { key: 'remark', label: '备注', type: 'textarea' }
    ]
  },
  expense: {
    label: '费用',
    primaryLabel: '费用类型',
    dateLabel: '费用日期',
    idKey: 'expenseId',
    statuses: [{ label: '已记录', value: 'recorded' }, { label: '已报销', value: 'reimbursed' }],
    fields: [
      { key: 'expenseType', label: '费用类型', required: true },
      { key: 'customerId', label: '客户', type: 'customer' },
      { key: 'projectId', label: '项目', type: 'project' },
      { key: 'amount', label: '费用金额', type: 'money', required: true },
      { key: 'expenseDate', label: '费用日期', type: 'date' },
      { key: 'status', label: '状态', type: 'status' },
      { key: 'remark', label: '备注', type: 'textarea' }
    ]
  }
}

const activeMeta = computed(() => metas[activeTab.value])
const activeEntityType = computed<EntityType>(() => `finance_${activeTab.value}` as EntityType)
const canCreate = computed(() => userStore.hasPermission('finance:create'))
const canEdit = computed(() => userStore.hasPermission('finance:edit'))
const canDelete = computed(() => userStore.hasPermission('finance:delete'))
const canExport = computed(() => userStore.hasPermission('finance:export'))
const amountToneClass = computed(() => activeTab.value === 'expense' ? 'finance-amount--expense' : 'finance-amount--income')
const dialogTitle = computed(() => `${editingRecord.value ? '编辑' : '新增'}${activeMeta.value.label}`)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / limit.value)))
const visiblePages = computed(() => {
  const maxVisible = 5
  const maxPage = totalPages.value
  let start = Math.max(1, page.value - Math.floor(maxVisible / 2))
  const end = Math.min(maxPage, start + maxVisible - 1)
  start = Math.max(1, end - maxVisible + 1)
  const pages: number[] = []
  for (let current = start; current <= end; current += 1) pages.push(current)
  return pages
})
const detailItems = computed(() => {
  const row = selectedRecord.value
  if (!row) return []
  return [
    { label: activeMeta.value.primaryLabel, value: primaryText(row) },
    { label: '客户', value: row.customerName || row.customerId },
    { label: '项目', value: row.projectName || row.projectId },
    { label: '金额', value: money(row.amount) },
    { label: activeMeta.value.dateLabel, value: rowDate(row) },
    { label: '状态', value: statusLabel(row.status) },
    { label: '来源', value: row.aiCreated ? 'AI 写入' : '手动录入' },
    { label: '创建人', value: row.createUserName || row.createUserId },
    { label: '创建时间', value: row.createTime },
    { label: '更新时间', value: row.updateTime },
    { label: '备注', value: row.remark }
  ]
})

onMounted(() => {
  void loadRecords()
  void preloadOptions()
  void loadCustomFields()
})

function goCustomer(customerId?: string) {
  if (customerId) void router.push(`/customer/${customerId}`)
}

function goProject(projectId?: string) {
  if (projectId) void router.push({ name: 'ProjectDetail', params: { id: projectId } })
}

function handleTabChange() {
  page.value = 1
  query.status = ''
  selectedRecord.value = null
  void loadRecords()
  void loadCustomFields()
}

async function loadRecords() {
  loading.value = true
  try {
    const payload = {
      ...buildQueryPayload(),
      page: page.value,
      limit: limit.value,
      startDate: dateRange.value?.[0],
      endDate: dateRange.value?.[1]
    }
    const response = await queryByTab(activeTab.value, payload)
    records.value = response.list || []
    total.value = response.totalRow || 0
    if (selectedRecord.value) {
      const idKey = activeMeta.value.idKey
      selectedRecord.value = records.value.find(item => item[idKey] === selectedRecord.value?.[idKey]) || null
    }
  } finally {
    loading.value = false
  }
}

function handlePageChange(nextPage: number) {
  const boundedPage = Math.min(Math.max(nextPage, 1), totalPages.value)
  if (boundedPage === page.value) return
  page.value = boundedPage
  void loadRecords()
}

function openDetail(row: FinanceRecordVO) {
  const id = row[activeMeta.value.idKey] || row.id
  if (id) {
    void router.push({ name: 'FinanceDetail', params: { type: activeTab.value, id: String(id) } })
  }
}

function openCreateDialog() {
  editingRecord.value = null
  resetForm()
  dialogVisible.value = true
}

function openEditDialog(row: FinanceRecordVO) {
  editingRecord.value = row
  resetForm(row)
  dialogVisible.value = true
}

function resetForm(row?: FinanceRecordVO) {
  for (const key of Object.keys(form)) delete form[key]
  for (const key of Object.keys(customForm)) delete customForm[key]
  for (const field of activeMeta.value.fields) {
    const value = row ? (row as any)[field.key] ?? '' : ''
    form[field.key] = field.type === 'date' ? dateOnly(value) : value
  }
  for (const field of customFields.value) {
    customForm[field.fieldName] = row?.customFields?.[field.fieldName] ?? defaultCustomFieldValue(field)
  }
  if (row) seedOptionsFromRecord(row)
  if (!row) {
    const today = new Date().toISOString().slice(0, 10)
    if (activeTab.value === 'contract') form.signDate = today
    if (activeTab.value === 'payment') form.paymentDate = today
    if (activeTab.value === 'invoice') form.invoiceDate = today
    if (activeTab.value === 'expense') form.expenseDate = today
  }
  if (row) {
    const id = row[activeMeta.value.idKey]
    if (id) form[activeMeta.value.idKey as string] = id
  }
}

async function saveRecord() {
  saving.value = true
  try {
    await saveByTab(activeTab.value, buildSavePayload())
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await loadRecords()
  } finally {
    saving.value = false
  }
}

async function loadCustomFields() {
  try {
    customFields.value = await getFormFieldsByEntity(activeEntityType.value)
  } catch {
    customFields.value = []
  }
}

function defaultCustomFieldValue(field: CustomField) {
  if (field.defaultValue !== undefined && field.defaultValue !== null && field.defaultValue !== '') return field.defaultValue
  if (field.fieldType === 'multiselect') return []
  if (field.fieldType === 'checkbox') return false
  return ''
}

function customFieldDisplay(field: CustomField, value: unknown) {
  return formatCustomFieldValue(field, value) || '-'
}

async function handleDelete(row: FinanceRecordVO) {
  const id = row[activeMeta.value.idKey]
  if (!id) return
  await ElMessageBox.confirm(`确认删除该${activeMeta.value.label}记录？`, '删除确认', { type: 'warning' })
  await deleteByTab(activeTab.value, String(id))
  ElMessage.success('已删除')
  if (selectedRecord.value?.[activeMeta.value.idKey] === id) selectedRecord.value = null
  await loadRecords()
}

function openImportPicker() {
  if (!canCreate.value) return
  importInputRef.value?.click()
}

async function handleExport() {
  if (exporting.value || !canExport.value) return
  exporting.value = true
  try {
    const exportRows = await fetchAllExportRows()
    downloadTextFile(
      buildFinanceCsv(exportRows),
      `财务${activeMeta.value.label}_${new Date().toISOString().slice(0, 10)}.csv`
    )
    ElMessage.success('导出成功')
  } finally {
    exporting.value = false
  }
}

async function handleImportFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  try {
    const rows = parseCsv(await file.text())
    if (!rows.length) {
      ElMessage.warning('导入文件为空')
      return
    }
    let successCount = 0
    editingRecord.value = null
    for (const row of rows) {
      const payload = buildImportPayload(row)
      if (!hasRequiredImportFields(payload)) continue
      await saveByTab(activeTab.value, payload)
      successCount += 1
    }
    if (successCount) {
      ElMessage.success(`已导入 ${successCount} 条${activeMeta.value.label}`)
      await loadRecords()
    } else {
      ElMessage.warning('没有可导入的数据，请检查必填字段')
    }
  } catch {
    ElMessage.error('导入失败，请检查 CSV 格式')
  }
}

async function preloadOptions() {
  await Promise.all([searchCustomers(''), searchProjects(''), searchContracts(''), searchReceivables('')])
}

function mergeOption(options: SelectOption[], option: SelectOption | null) {
  if (!option?.value) return options
  return options.some(item => item.value === option.value) ? options : [option, ...options]
}

function seedOptionsFromRecord(row: FinanceRecordVO) {
  customerOptions.value = mergeOption(customerOptions.value, row.customerId ? { value: row.customerId, label: row.customerName || row.customerId } : null)
  projectOptions.value = mergeOption(projectOptions.value, row.projectId ? { value: row.projectId, label: row.projectName || row.projectId } : null)
  contractOptions.value = mergeOption(contractOptions.value, row.contractId ? { value: row.contractId, label: row.contractName || row.contractId } : null)
  receivableOptions.value = mergeOption(receivableOptions.value, row.receivableId ? { value: row.receivableId, label: row.title || row.receivableId } : null)
}

async function searchCustomers(keyword: string) {
  customerLoading.value = true
  try {
    const response = await queryCustomerList({ keyword, page: 1, limit: 20 })
    customerOptions.value = (response.list || []).map(item => ({
      value: String(item.customerId),
      label: item.companyName || String(item.customerId)
    }))
  } finally {
    customerLoading.value = false
  }
}

async function searchProjects(keyword: string) {
  projectLoading.value = true
  try {
    const response = await queryProjectPageList({ keyword, page: 1, limit: 20 })
    projectOptions.value = (response.list || []).map(item => ({
      value: String(item.projectId),
      label: item.name || String(item.projectId)
    }))
  } finally {
    projectLoading.value = false
  }
}

async function searchContracts(keyword: string) {
  contractLoading.value = true
  try {
    const response = await queryFinanceContracts({ keyword, page: 1, limit: 20 })
    contractOptions.value = (response.list || []).map(item => ({
      value: String(item.contractId || item.id),
      label: item.contractName || item.title || String(item.contractId || item.id)
    }))
  } finally {
    contractLoading.value = false
  }
}

async function searchReceivables(keyword: string) {
  receivableLoading.value = true
  try {
    const response = await queryFinanceReceivables({ keyword, page: 1, limit: 20 })
    receivableOptions.value = (response.list || []).map(item => ({
      value: String(item.receivableId || item.id),
      label: item.title || item.contractName || String(item.receivableId || item.id)
    }))
  } finally {
    receivableLoading.value = false
  }
}

function queryByTab(tab: FinanceListTab, payload: any) {
  switch (tab) {
    case 'contract': return queryFinanceContracts(payload)
    case 'receivable': return queryFinanceReceivables(payload)
    case 'payment': return queryFinancePayments(payload)
    case 'invoice': return queryFinanceInvoices(payload)
    case 'expense': return queryFinanceExpenses(payload)
  }
}

function buildQueryPayload() {
  const payload: Record<string, any> = {
    keyword: query.keyword || undefined,
    startDate: dateRange.value?.[0],
    endDate: dateRange.value?.[1]
  }
  if (activeMeta.value.statuses.length > 0 && query.status) {
    payload.status = query.status
  }
  return payload
}

function buildSavePayload() {
  const payload: Record<string, any> = {
    ...form,
    customFields: { ...customForm }
  }
  if (editingRecord.value) {
    payload.ownerId = editingRecord.value.ownerId || payload.ownerId
    payload.sourceType = editingRecord.value.sourceType
    payload.sourceText = editingRecord.value.sourceText
    payload.aiCreated = editingRecord.value.aiCreated
  }
  return payload
}

async function fetchAllExportRows() {
  const rows: FinanceRecordVO[] = []
  let currentPage = 1
  let expectedTotal = Number(total.value || 0)

  while (currentPage <= 1000) {
    const response = await queryByTab(activeTab.value, {
      ...buildQueryPayload(),
      page: currentPage,
      limit: EXPORT_PAGE_SIZE
    })
    const pageRows = response.list || []
    rows.push(...pageRows)
    expectedTotal = Math.max(expectedTotal, Number(response.totalRow || 0))
    if (pageRows.length < EXPORT_PAGE_SIZE || rows.length >= expectedTotal) break
    currentPage += 1
  }

  return expectedTotal > 0 ? rows.slice(0, expectedTotal) : rows
}

function saveByTab(tab: FinanceListTab, payload: any) {
  const editing = Boolean(editingRecord.value)
  switch (tab) {
    case 'contract': return editing ? updateFinanceContract(payload) : addFinanceContract(payload)
    case 'receivable': return editing ? updateFinanceReceivable(payload) : addFinanceReceivable(payload)
    case 'payment': return editing ? updateFinancePayment(payload) : addFinancePayment(payload)
    case 'invoice': return editing ? updateFinanceInvoice(payload) : addFinanceInvoice(payload)
    case 'expense': return editing ? updateFinanceExpense(payload) : addFinanceExpense(payload)
  }
}

function deleteByTab(tab: FinanceListTab, id: string) {
  switch (tab) {
    case 'contract': return deleteFinanceContract(id)
    case 'receivable': return deleteFinanceReceivable(id)
    case 'payment': return deleteFinancePayment(id)
    case 'invoice': return deleteFinanceInvoice(id)
    case 'expense': return deleteFinanceExpense(id)
  }
}

function primaryText(row: FinanceRecordVO) {
  return row.contractName || row.title || row.expenseType || row.invoiceNo || row.paymentMethod || row.remark || '财务记录'
}

function rowDate(row: FinanceRecordVO) {
  return dateOnly(row.signDate || row.dueDate || row.paymentDate || row.invoiceDate || row.expenseDate)
}

function money(value: unknown) {
  const amount = Number(value || 0)
  return amount.toLocaleString('zh-CN', { style: 'currency', currency: 'CNY' })
}

function statusLabel(status?: string) {
  const map: Record<string, string> = {
    active: '执行中',
    completed: '完成',
    terminated: '终止',
    pending: '待收',
    partial: '部分回款',
    paid: '已收',
    overdue: '逾期',
    issued: '已开票',
    voided: '已作废',
    recorded: '已记录',
    reimbursed: '已报销'
  }
  return status ? map[status] || status : '-'
}

function statusType(status?: string) {
  if (status === 'paid' || status === 'completed') return 'success'
  if (status === 'overdue' || status === 'voided') return 'danger'
  if (status === 'partial') return 'warning'
  return 'info'
}

function dateOnly(value: unknown) {
  const text = String(value || '').trim()
  if (!text) return ''
  const matched = text.match(/^\d{4}-\d{2}-\d{2}/)
  return matched?.[0] || text
}

function buildFinanceCsv(rows: FinanceRecordVO[]) {
  const baseFields = activeMeta.value.fields
  const headers = [...baseFields.map(field => field.label), ...customFields.value.map(field => field.fieldLabel)]
  const lines = [
    headers.map(escapeCsvCell).join(','),
    ...rows.map(row => {
      const values = [
        ...baseFields.map(field => exportFieldValue(row, field)),
        ...customFields.value.map(field => row.customFields?.[field.fieldName] ?? '')
      ]
      return values.map(escapeCsvCell).join(',')
    })
  ]
  return `\ufeff${lines.join('\n')}`
}

function exportFieldValue(row: FinanceRecordVO, field: FieldMeta) {
  if (field.type === 'date') return dateOnly((row as any)[field.key])
  if (field.type === 'status') return statusLabel((row as any)[field.key])
  if (field.key === 'customerId') return row.customerName || row.customerId || ''
  if (field.key === 'projectId') return row.projectName || row.projectId || ''
  if (field.key === 'contractId') return row.contractName || row.contractId || ''
  if (field.key === 'receivableId') return row.title || row.receivableId || ''
  return (row as any)[field.key] ?? ''
}

function escapeCsvCell(value: unknown) {
  const text = String(value ?? '')
  return /[",\n\r]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text
}

function downloadTextFile(content: string, filename: string) {
  const blob = new Blob([content], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  document.body.appendChild(anchor)
  anchor.click()
  document.body.removeChild(anchor)
  URL.revokeObjectURL(url)
}

function parseCsv(content: string) {
  const rows: string[][] = []
  let current = ''
  let row: string[] = []
  let inQuotes = false
  for (let index = 0; index < content.length; index += 1) {
    const char = content[index]
    const next = content[index + 1]
    if (char === '"' && inQuotes && next === '"') {
      current += '"'
      index += 1
    } else if (char === '"') {
      inQuotes = !inQuotes
    } else if (char === ',' && !inQuotes) {
      row.push(current)
      current = ''
    } else if ((char === '\n' || char === '\r') && !inQuotes) {
      if (char === '\r' && next === '\n') index += 1
      row.push(current)
      if (row.some(cell => cell.trim())) rows.push(row)
      row = []
      current = ''
    } else {
      current += char
    }
  }
  row.push(current)
  if (row.some(cell => cell.trim())) rows.push(row)
  if (rows.length < 2) return []
  const headers = rows[0].map(header => header.replace(/^\ufeff/, '').trim())
  return rows.slice(1).map(cells => Object.fromEntries(headers.map((header, index) => [header, (cells[index] || '').trim()])))
}

function buildImportPayload(row: Record<string, string>) {
  const payload: Record<string, any> = {}
  for (const field of activeMeta.value.fields) {
    const rawValue = row[field.label] ?? row[field.key] ?? ''
    if (field.type === 'money') payload[field.key] = rawValue ? Number(String(rawValue).replace(/[￥¥,\s]/g, '')) : undefined
    else if (field.type === 'date') payload[field.key] = dateOnly(rawValue)
    else if (field.type === 'status') payload[field.key] = importStatusValue(rawValue)
    else if (field.type === 'customer' || field.type === 'project' || field.type === 'contract' || field.type === 'receivable') payload[field.key] = importRelationValue(field.type, rawValue)
    else payload[field.key] = rawValue
  }
  const importedCustomFields: Record<string, any> = {}
  for (const field of customFields.value) {
    const rawValue = row[field.fieldLabel] ?? row[field.fieldName]
    if (rawValue !== undefined) importedCustomFields[field.fieldName] = rawValue
  }
  return { ...payload, customFields: importedCustomFields }
}

function importStatusValue(value: string) {
  const rawValue = String(value || '').trim()
  if (!rawValue) return ''
  const matched = activeMeta.value.statuses.find(item => item.label === rawValue || item.value === rawValue)
  return matched?.value || rawValue
}

function importRelationValue(type: NonNullable<FieldMeta['type']>, value: string) {
  const rawValue = String(value || '').trim()
  if (!rawValue) return ''
  const optionsMap: Partial<Record<NonNullable<FieldMeta['type']>, SelectOption[]>> = {
    customer: customerOptions.value,
    project: projectOptions.value,
    contract: contractOptions.value,
    receivable: receivableOptions.value
  }
  const matched = optionsMap[type]?.find(option => option.label === rawValue || option.value === rawValue)
  return matched?.value || rawValue
}

function hasRequiredImportFields(payload: Record<string, any>) {
  return activeMeta.value.fields.every(field => !field.required || payload[field.key] !== undefined && payload[field.key] !== '')
}
</script>

<style scoped>
.finance-page :deep(.el-input__wrapper),
.finance-page :deep(.el-select__wrapper),
.finance-page :deep(.el-date-editor.el-input__wrapper) {
  border-radius: 16px;
  box-shadow: 0 0 0 1px #e7e0d6 inset;
}

.finance-tabs :deep(.el-tabs__header) {
  margin: 0;
}

.finance-tabs :deep(.el-tabs__nav-wrap::after) {
  height: 1px;
  background-color: #e7e0d6;
}

.finance-tabs :deep(.el-tabs__item) {
  height: 44px;
  color: #1f1d1a;
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 0;
}

.finance-tabs :deep(.el-tabs__active-bar) {
  background-color: #1f1d1a;
}

.finance-create-button {
  display: inline-flex;
  height: 40px;
  align-items: center;
  gap: 6px;
  border-radius: 12px;
  background: var(--wk-primary);
  padding: 0 16px;
  color: #fff;
  font-size: 14px;
  font-weight: 700;
  box-shadow: 0 10px 18px rgb(15 23 42 / 0.16);
  transition: background-color 0.15s ease, border-color 0.15s ease, color 0.15s ease;
}

.finance-create-button:hover,
.finance-create-button:focus {
  background: color-mix(in srgb, var(--wk-primary) 90%, white);
  color: #fff;
}

.finance-icon-btn {
  display: inline-flex;
  width: 30px;
  height: 30px;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  color: #64748b;
  transition: background-color 0.15s ease, color 0.15s ease;
}

.finance-icon-btn:hover {
  background: #f1f5f9;
  color: #0f172a;
}

.finance-amount {
  display: inline-flex;
  max-width: 100%;
  justify-content: flex-end;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", monospace;
  font-size: 13px;
  font-weight: 800;
  letter-spacing: 0;
  line-height: 20px;
  white-space: nowrap;
}

.finance-amount--income {
  color: #0f172a;
}

.finance-amount--expense {
  color: #b42318;
}

.finance-amount--risk {
  color: #d97706;
}

.finance-detail-row {
  border-bottom: 1px solid rgb(241 245 249);
  padding-bottom: 10px;
}

.finance-related-link {
  display: block;
  width: 100%;
  border-radius: 6px;
  background: #f8fafc;
  padding: 10px 12px;
  text-align: left;
  color: #0f172a;
}

.finance-related-link:hover {
  background: #f1f5f9;
}

.wk-customer-table :deep(.el-table__inner-wrapper::before) {
  display: none;
}

.wk-customer-table {
  --el-table-bg-color: var(--wk-bg-surface);
  --el-table-tr-bg-color: var(--wk-bg-surface);
  --el-table-header-bg-color: var(--wk-bg-surface-subtle);
  --el-table-header-text-color: var(--wk-text-muted);
  --el-table-text-color: var(--wk-text-secondary);
  --el-table-border-color: var(--wk-border-subtle);
  --el-table-row-hover-bg-color: transparent;
  --wk-customer-table-row-hover-bg-color: color-mix(in srgb, var(--wk-primary) 11%, var(--wk-bg-surface));
}

.wk-customer-table :deep(.el-table__border-left-patch),
.wk-customer-table :deep(.el-table__fixed-right-patch) {
  background: var(--wk-bg-surface-subtle);
}

.wk-customer-table :deep(th.el-table__cell) {
  background: var(--wk-bg-surface-subtle);
  color: var(--wk-text-muted);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  padding: 16px 0;
  border-bottom: 1px solid var(--wk-border-muted);
}

.wk-customer-table :deep(td.el-table__cell) {
  padding: 16px 0;
  border-bottom: 1px solid var(--wk-border-subtle);
}

.wk-customer-table :deep(.el-table__row) {
  cursor: pointer;
}

.wk-customer-table :deep(.el-table__body tr:hover > td.el-table__cell) {
  background-color: var(--wk-customer-table-row-hover-bg-color);
}

.wk-customer-table :deep(.el-table__empty-block) {
  min-height: 220px;
}

@media (max-width: 767px) {
  .wk-customer-pagination-bar {
    padding-bottom: calc(1rem + var(--safe-area-inset-bottom)) !important;
  }
}

:global(.wk-customer-import-export-dropdown.el-popper) {
  border-color: #e2e8f0;
  border-radius: 8px;
  box-shadow: 0 16px 36px rgba(15, 23, 42, 0.14);
}

:global(.wk-customer-import-export-dropdown .el-popper__arrow) {
  display: none;
}

:global(.wk-customer-import-export-dropdown .el-dropdown-menu) {
  padding: 6px;
}

:global(.wk-customer-import-export-dropdown .el-dropdown-menu__item) {
  border-radius: 6px;
  color: #475569;
  font-size: 13px;
  font-weight: 600;
}

:global(.wk-customer-import-export-dropdown .el-dropdown-menu__item:not(.is-disabled):hover),
:global(.wk-customer-import-export-dropdown .el-dropdown-menu__item:not(.is-disabled):focus) {
  background: #f8fafc;
  color: var(--wk-primary);
}
</style>
