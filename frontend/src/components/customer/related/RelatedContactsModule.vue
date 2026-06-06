<template>
  <section v-if="visible" :class="sectionClasses">
    <div class="mb-4 flex items-center justify-between">
      <h4 class="flex items-center gap-2 text-sm font-bold text-slate-900">
        <span :class="sectionIconBoxClass" :style="{ backgroundColor: '#cf744f' }">
          <span class="material-symbols-outlined text-[17px] leading-none">group</span>
        </span>
        联系人
        <button
          v-if="showToggle"
          type="button"
          class="group/module-action relative inline-flex size-7 shrink-0 items-center justify-center rounded-lg bg-white text-slate-500 transition-[background-color,color,border-color] hover:bg-[#efefef] hover:text-[#0d0d0d]"
          :aria-expanded="expanded"
          :aria-label="expanded ? '收起联系人' : '展开联系人'"
          @click="emit('update:expanded', !expanded)"
        >
          <span class="material-symbols-outlined text-[16px] leading-none">
            {{ expanded ? 'keyboard_arrow_down' : 'keyboard_arrow_right' }}
          </span>
          <span
            class="pointer-events-none absolute left-1/2 top-full z-[200] mt-2 -translate-x-1/2 whitespace-nowrap rounded-lg bg-black px-3 py-1.5 text-[13px] font-medium text-white opacity-0 shadow-md transition-opacity duration-150 group-hover/module-action:opacity-100"
            role="tooltip"
          >
            {{ expanded ? '收起联系人' : '展开联系人' }}
          </span>
        </button>
      </h4>
      <div class="flex shrink-0 items-center gap-2">
        <button
          v-if="canCreate"
          type="button"
          class="group/module-action relative flex size-7 items-center justify-center rounded-lg border border-slate-200 bg-white text-slate-400 transition-all hover:border-primary/30 hover:bg-[#efefef] hover:text-primary"
          aria-label="名片上传"
          @click="emit('upload-card')"
        >
          <span class="material-symbols-outlined text-[18px] leading-none">contact_page</span>
          <span
            class="pointer-events-none absolute right-full top-1/2 z-[200] mr-2 -translate-y-1/2 whitespace-nowrap rounded-lg bg-black px-3 py-1.5 text-[13px] font-medium text-white opacity-0 shadow-md transition-opacity duration-150 group-hover/module-action:opacity-100"
            role="tooltip"
          >
            名片上传
          </span>
        </button>
        <button
          v-if="canCreate"
          type="button"
          class="group/module-action relative flex size-7 items-center justify-center rounded-lg border border-slate-200 bg-white text-slate-400 transition-all hover:border-primary/30 hover:bg-[#efefef] hover:text-primary"
          aria-label="新建联系人"
          @click="emit('add')"
        >
          <span class="material-symbols-outlined wk-plus-button-icon wk-plus-button-icon--compact">person_add</span>
          <span
            class="pointer-events-none absolute right-full top-1/2 z-[200] mr-2 -translate-y-1/2 whitespace-nowrap rounded-lg bg-black px-3 py-1.5 text-[13px] font-medium text-white opacity-0 shadow-md transition-opacity duration-150 group-hover/module-action:opacity-100"
            role="tooltip"
          >
            新建联系人
          </span>
        </button>
      </div>
    </div>

    <div v-if="isModuleVisible" class="space-y-4">
      <div v-if="loading" class="space-y-3">
        <div
          v-for="index in 3"
          :key="`contact-skeleton-${index}`"
          class="rounded-2xl border border-slate-200 bg-white p-4"
        >
          <div class="flex flex-col gap-3">
            <div class="flex items-start justify-between gap-3">
              <div class="min-w-0 flex flex-1 items-center gap-3">
                <div class="size-10 shrink-0 animate-pulse rounded-xl bg-slate-100" />
                <div class="min-w-0 flex-1 space-y-2">
                  <div class="h-4 w-24 animate-pulse rounded-full bg-slate-100" />
                  <div class="h-3 w-16 animate-pulse rounded-full bg-slate-100" />
                </div>
              </div>
              <div class="h-6 w-20 shrink-0 animate-pulse rounded-lg bg-slate-100" />
            </div>
            <div class="h-px w-full bg-slate-100" />
            <div class="space-y-2">
              <div class="h-3 w-2/3 animate-pulse rounded-full bg-slate-100" />
              <div class="h-3 w-4/5 animate-pulse rounded-full bg-slate-100" />
              <div class="h-3 w-1/2 animate-pulse rounded-full bg-slate-100" />
            </div>
          </div>
        </div>
      </div>
      <RelatedEmptyState v-else-if="contacts.length === 0" icon="person_off" text="暂无关联联系人" />
      <template v-else>
        <div
          v-for="contact in contacts"
          :key="contact.contactId"
          class="group relative cursor-pointer overflow-hidden rounded-2xl border border-slate-200 bg-gradient-to-br from-white via-white to-slate-50/80 p-4 transition-all duration-200 hover:-translate-y-0.5 hover:border-primary/30 hover:shadow-lg hover:shadow-slate-200/70"
          @click="emit('view', contact)"
        >
          <div class="pointer-events-none absolute inset-x-0 top-0 h-16 bg-gradient-to-b from-slate-50 to-transparent opacity-0 transition-opacity duration-200 group-hover:opacity-100" />
          <div class="relative flex flex-col gap-3">
            <div class="flex items-start justify-between gap-3">
              <div class="min-w-0 flex flex-1 items-center gap-3">
                <div class="flex size-10 shrink-0 items-center justify-center rounded-xl border border-slate-200 bg-slate-50 text-sm font-bold text-slate-600 transition-colors group-hover:border-primary/20 group-hover:bg-primary/10 group-hover:text-primary">
                  {{ contact.name?.trim()?.charAt(0) || '?' }}
                </div>
                <div class="min-w-0 flex-1">
                  <div class="flex min-w-0 items-center gap-1.5">
                    <h5 class="min-w-0 truncate text-sm font-bold leading-tight text-slate-900">{{ contact.name || '-' }}</h5>
                    <button
                      v-if="!isPrimaryContact(contact) && canSetPrimary"
                      type="button"
                      class="group/module-action relative flex size-7 shrink-0 items-center justify-center rounded-full bg-amber-50 text-amber-600 transition-all hover:bg-amber-100"
                      aria-label="设为主要联系人"
                      @click.stop="emit('set-primary', contact.contactId)"
                    >
                      <span
                        class="material-symbols-outlined text-[18px] leading-none"
                        style="font-variation-settings: 'FILL' 0, 'wght' 500, 'GRAD' 0, 'opsz' 24"
                      >star</span>
                      <span
                        class="pointer-events-none absolute left-1/2 top-full z-[200] mt-2 -translate-x-1/2 whitespace-nowrap rounded-lg bg-black px-3 py-1.5 text-[13px] font-medium text-white opacity-0 shadow-md transition-opacity duration-150 group-hover/module-action:opacity-100"
                        role="tooltip"
                      >
                        设为主要联系人
                      </span>
                    </button>
                    <button
                      v-if="canCreateRelation"
                      type="button"
                      class="group/module-action pointer-events-none relative flex size-7 shrink-0 items-center justify-center rounded-full bg-slate-50 text-slate-500 opacity-0 transition-all hover:bg-primary/10 hover:text-primary group-hover:pointer-events-auto group-hover:opacity-100"
                      aria-label="添加到关系"
                      @click.stop="emit('add-to-relation', contact)"
                    >
                      <span class="material-symbols-outlined text-[18px] leading-none">diversity_3</span>
                      <span
                        class="pointer-events-none absolute left-1/2 top-full z-[200] mt-2 -translate-x-1/2 whitespace-nowrap rounded-lg bg-black px-3 py-1.5 text-[13px] font-medium text-white opacity-0 shadow-md transition-opacity duration-150 group-hover/module-action:opacity-100"
                        role="tooltip"
                      >
                        添加到关系
                      </span>
                    </button>
                  </div>
                  <p class="mt-0.5 truncate text-[11px] font-medium text-slate-500">{{ contact.position || '-' }}</p>
                </div>
              </div>
              <span
                v-if="isPrimaryContact(contact)"
                class="inline-flex shrink-0 items-center gap-1 rounded-lg bg-primary/10 px-2 py-1 text-[11px] font-bold text-primary"
              >
                <span class="material-symbols-outlined text-[12px] leading-none">stars</span>
                主要联系人
              </span>
            </div>

            <div class="h-px w-full bg-slate-100" />

            <div class="space-y-2">
              <div class="flex min-w-0 items-center gap-3 text-slate-600 transition-colors group-hover:text-slate-700">
                <span class="material-symbols-outlined shrink-0 text-[18px] leading-none text-slate-400">call</span>
                <span :class="contact.phone ? 'font-mono text-xs font-medium tracking-tight text-slate-700' : 'text-xs font-medium text-slate-400'">
                  {{ contact.phone || '-' }}
                </span>
              </div>
              <div class="flex min-w-0 items-center gap-3 text-slate-600 transition-colors group-hover:text-slate-700">
                <span class="material-symbols-outlined shrink-0 text-[18px] leading-none text-slate-400">mail</span>
                <span
                  class="min-w-0 truncate text-xs font-medium tracking-tight"
                  :class="contact.email ? 'text-slate-700' : 'text-slate-400'"
                >
                  {{ contact.email || '-' }}
                </span>
              </div>
              <div class="flex min-w-0 items-center gap-3 text-slate-600 transition-colors group-hover:text-slate-700">
                <span class="material-symbols-outlined shrink-0 text-[18px] leading-none text-slate-400">chat</span>
                <span
                  class="min-w-0 truncate text-xs font-medium tracking-tight"
                  :class="contact.wechat ? 'text-slate-700' : 'text-slate-400'"
                >
                  {{ contact.wechat ? `${contact.wechat}` : '-' }}
                </span>
              </div>
            </div>
          </div>
        </div>
      </template>
      <div v-if="total > pageSize" class="flex justify-center pt-2">
        <el-pagination
          :current-page="page"
          :page-size="pageSize"
          :total="total"
          layout="prev, pager, next"
          small
          @current-change="handlePageChange"
        />
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { Contact } from '@/types/customer'
import RelatedEmptyState from './RelatedEmptyState.vue'

const props = withDefaults(defineProps<{
  contacts?: Contact[]
  loading?: boolean
  visible?: boolean
  embeddedLayout?: boolean
  expanded?: boolean
  canCreate?: boolean
  canSetPrimary?: boolean
  canCreateRelation?: boolean
  total?: number
  page?: number
  pageSize?: number
}>(), {
  contacts: () => [],
  loading: false,
  visible: true,
  embeddedLayout: true,
  expanded: true,
  canCreate: true,
  canSetPrimary: false,
  canCreateRelation: false,
  total: 0,
  page: 1,
  pageSize: 100
})

const emit = defineEmits<{
  (e: 'update:expanded', value: boolean): void
  (e: 'update:page', value: number): void
  (e: 'upload-card'): void
  (e: 'add'): void
  (e: 'view', contact: Contact): void
  (e: 'set-primary', contactId: string): void
  (e: 'add-to-relation', contact: Contact): void
}>()

const sectionIconBoxClass = 'inline-flex size-7 shrink-0 items-center justify-center rounded-lg text-white shadow-sm'
const showToggle = computed(() => props.loading || props.contacts.length > 0)
const isModuleVisible = computed(() => props.expanded || !showToggle.value)
const sectionClasses = computed(() => [
  'wk-related-contacts group/contacts-module',
  props.embeddedLayout
    ? 'mt-5 border-t border-slate-100 pt-5'
    : 'rounded-2xl border border-slate-200 bg-white p-4 shadow-sm'
])

function handlePageChange(value: number) {
  emit('update:page', value)
}

function isPrimaryContact(contact?: Pick<Contact, 'isPrimary'> | null): boolean {
  const value = contact?.isPrimary as boolean | number | string | undefined
  return value === true || value === 1 || value === '1'
}
</script>
