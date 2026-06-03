<template>
  <div class="flex h-full flex-col overflow-hidden bg-[#f7f7f5]">
    <div class="flex-1 overflow-y-auto px-4 py-6 md:px-6">
      <div class="mx-auto flex max-w-7xl flex-col gap-6">
        <section class="rounded-[32px] bg-[linear-gradient(135deg,#0f172a_0%,#1e293b_55%,#334155_100%)] px-6 py-7 text-white shadow-xl shadow-slate-900/10 md:px-8">
          <div class="flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between">
            <div>
              <p class="text-xs font-bold uppercase tracking-[0.3em] text-slate-300">Project Hub</p>
              <h1 class="mt-3 text-2xl font-black tracking-tight md:text-3xl">项目模块</h1>
              <p class="mt-3 max-w-2xl text-sm leading-6 text-slate-300">
                现在你可以围绕一个项目统一管理任务、AI 沟通和泳道看板，不再只看散落的客户任务。
              </p>
            </div>
            <button
              type="button"
              class="inline-flex items-center justify-center gap-2 self-start rounded-2xl bg-white px-4 py-3 text-sm font-bold text-slate-900 transition-transform hover:-translate-y-0.5"
              @click="openCreateDialog"
            >
              <span class="material-symbols-outlined text-[18px]">add</span>
              新建项目
            </button>
          </div>
        </section>

        <section class="grid grid-cols-1 gap-4 md:grid-cols-3">
          <article class="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
            <p class="text-sm text-slate-500">项目总数</p>
            <p class="mt-3 text-3xl font-black text-slate-900">{{ projectStore.accessibleProjectSummaries.length }}</p>
          </article>
          <article class="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
            <p class="text-sm text-slate-500">未完成任务</p>
            <p class="mt-3 text-3xl font-black text-slate-900">{{ totalIncompleteTasks }}</p>
          </article>
          <article class="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
            <p class="text-sm text-slate-500">进行中项目</p>
            <p class="mt-3 text-3xl font-black text-slate-900">{{ activeProjects }}</p>
          </article>
        </section>

        <section class="rounded-[28px] border border-slate-200 bg-white shadow-sm">
          <div class="flex items-center justify-between border-b border-slate-100 px-5 py-4 md:px-6">
            <div>
              <h2 class="text-lg font-bold text-slate-900">全部项目</h2>
              <p class="mt-1 text-sm text-slate-500">点击任一项目即可进入 AI 对话与任务泳道。</p>
            </div>
          </div>

          <div v-if="projectStore.accessibleProjectSummaries.length === 0" class="px-6 py-20 text-center text-slate-400">
            <span class="material-symbols-outlined text-5xl">folder_open</span>
            <p class="mt-4 text-sm">还没有项目，先创建一个项目开始管理任务吧。</p>
          </div>

          <div v-else class="grid grid-cols-1 gap-4 p-4 md:p-5 xl:grid-cols-2">
            <article
              v-for="project in projectStore.accessibleProjectSummaries"
              :key="project.projectId"
              class="group rounded-[28px] border border-slate-200 bg-[#fbfaf8] p-5 transition-all hover:-translate-y-0.5 hover:border-primary/30 hover:shadow-lg hover:shadow-slate-200/60"
            >
              <div class="flex items-start justify-between gap-3">
                <div class="min-w-0 cursor-pointer" @click="goToProject(project.projectId)">
                  <div class="flex items-center gap-2">
                    <h3 class="truncate text-lg font-bold text-slate-900 transition-colors group-hover:text-primary">
                      {{ project.name }}
                    </h3>
                    <span class="inline-flex rounded-full px-2.5 py-1 text-xs font-bold" :class="projectStatusClass(project.status)">
                      {{ projectStatusLabel(project.status) }}
                    </span>
                  </div>
                  <p class="mt-2 line-clamp-2 text-sm leading-6 text-slate-500">
                    {{ project.description || '暂无项目描述，点击进入项目后可通过 AI 对话补充项目上下文。' }}
                  </p>
                </div>
                <button
                  v-if="projectStore.getUserProjectPermission(project.projectId, 'EDIT_PROJECT')"
                  type="button"
                  class="flex size-9 shrink-0 items-center justify-center rounded-2xl text-slate-400 transition-colors hover:bg-white hover:text-primary"
                  @click="openEditDialog(project.projectId)"
                >
                  <span class="material-symbols-outlined text-[18px]">edit</span>
                </button>
              </div>

              <dl class="mt-5 grid grid-cols-2 gap-3 text-sm">
                <div class="rounded-2xl border border-slate-200 bg-white px-4 py-3">
                  <dt class="text-xs uppercase tracking-wider text-slate-400">项目负责人</dt>
                  <dd class="mt-2 font-semibold text-slate-900">{{ project.ownerName || '未指定' }}</dd>
                </div>
                <div class="rounded-2xl border border-slate-200 bg-white px-4 py-3">
                  <dt class="text-xs uppercase tracking-wider text-slate-400">关联客户</dt>
                  <dd class="mt-2 font-semibold text-slate-900">{{ project.customerName || '未关联' }}</dd>
                </div>
                <div class="rounded-2xl border border-slate-200 bg-white px-4 py-3">
                  <dt class="text-xs uppercase tracking-wider text-slate-400">最近更新时间</dt>
                  <dd class="mt-2 font-semibold text-slate-900">{{ formatDateTime(project.updateTime) }}</dd>
                </div>
                <div class="rounded-2xl border border-slate-200 bg-white px-4 py-3">
                  <dt class="text-xs uppercase tracking-wider text-slate-400">项目任务</dt>
                  <dd class="mt-2 font-semibold text-slate-900">
                    {{ project.taskCount }} 个
                    <span class="ml-1 text-slate-400">/ 未完成 {{ project.incompleteTaskCount }} 个</span>
                  </dd>
                </div>
              </dl>

              <div class="mt-5 flex items-center justify-between">
                <div class="flex items-center gap-2 text-xs text-slate-400">
                  <span class="material-symbols-outlined text-[16px]">view_kanban</span>
                  默认支持 AI 对话与任务泳道
                </div>
                <button
                  type="button"
                  class="inline-flex items-center gap-1 rounded-xl bg-slate-900 px-3 py-2 text-sm font-semibold text-white transition-colors hover:bg-slate-700"
                  @click="goToProject(project.projectId)"
                >
                  进入项目
                  <span class="material-symbols-outlined text-[16px]">arrow_forward</span>
                </button>
              </div>
            </article>
          </div>
        </section>
      </div>
    </div>

    <ProjectUpsertDialog
      v-model="showDialog"
      :editing-project="editingProject"
      @submit="handleSubmitProject"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import ProjectUpsertDialog from '@/views/project/components/ProjectUpsertDialog.vue'
import { useProjectStore } from '@/stores/project'
import type { ProjectEntity } from '@/types/project'
import {
  formatDateTime,
  projectStatusClass,
  projectStatusLabel
} from '@/utils/project'

const router = useRouter()
const projectStore = useProjectStore()

const showDialog = ref(false)
const editingProject = ref<ProjectEntity | null>(null)

const totalIncompleteTasks = computed(() =>
  projectStore.accessibleProjectSummaries.reduce((sum, project) => sum + project.incompleteTaskCount, 0)
)

const activeProjects = computed(() =>
  projectStore.accessibleProjectSummaries.filter(project => project.status === 'IN_PROGRESS').length
)

onMounted(() => {
  void projectStore.ensureInitialized()
})

function openCreateDialog() {
  editingProject.value = null
  showDialog.value = true
}

function openEditDialog(projectId: string) {
  editingProject.value = projectStore.getProjectById(projectId)
  showDialog.value = true
}

async function handleSubmitProject(payload: {
  name: string
  description?: string
  customerId?: string
  customerName?: string
  ownerId?: string
  ownerName?: string
  startDate?: string
  dueDate?: string
  status: ProjectEntity['status']
}) {
  if (editingProject.value) {
    await projectStore.updateProject({
      projectId: editingProject.value.projectId,
      ...payload
    })
    ElMessage.success('项目信息已更新')
  } else {
    await projectStore.createProject(payload)
    ElMessage.success('项目创建成功')
  }
}

function goToProject(projectId: string) {
  router.push({ name: 'ProjectDetail', params: { id: projectId }, query: { view: 'board' } })
}
</script>
