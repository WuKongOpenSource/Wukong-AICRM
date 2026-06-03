<template>
  <div class="flex h-full flex-col overflow-hidden bg-[#f6f7f4]">
    <div v-if="project && canViewProject" class="flex flex-1 flex-col overflow-hidden">
      <header class="border-b border-slate-200 bg-white px-4 py-4 md:px-6">
        <div class="mx-auto flex max-w-7xl flex-col gap-4">
          <div class="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
            <div class="min-w-0">
              <button
                v-if="isTaskConversation"
                type="button"
                class="inline-flex items-center gap-1 text-sm font-medium text-slate-500 transition-colors hover:text-slate-800"
                @click="handleBackClick"
              >
                <span class="material-symbols-outlined text-[16px]">arrow_back</span>
                {{ isTaskConversation ? '返回项目' : '返回项目列表' }}
              </button>

              <div class="mt-3 flex flex-wrap items-center gap-3">
                <template v-if="isTaskConversation && currentTaskConversation">
                  <h1 class="text-2xl font-black tracking-tight text-slate-900">
                    当前对话对象：任务 - {{ currentTaskConversation.title }}
                  </h1>
                  <span class="inline-flex rounded-full px-3 py-1 text-sm font-bold bg-white border border-slate-200 text-slate-600">
                    所属项目：{{ project.name }}
                  </span>
                </template>
                <template v-else>
                  <h1 class="text-2xl font-black tracking-tight text-slate-900">{{ project.name }}</h1>
                  <span class="inline-flex rounded-full px-3 py-1 text-sm font-bold" :class="projectStatusClass(project.status)">
                    {{ projectStatusLabel(project.status) }}
                  </span>
                </template>
              </div>

              <div v-if="isTaskConversation && currentTaskConversation" class="mt-3 flex flex-wrap gap-3 text-sm text-slate-500">
                <span class="inline-flex items-center gap-1.5">
                  <span class="material-symbols-outlined text-[16px]">view_kanban</span>
                  {{ currentTaskConversation.status }}
                </span>
                <span class="inline-flex items-center gap-1.5">
                  <span class="material-symbols-outlined text-[16px]">person</span>
                  {{ currentTaskConversation.ownerName || '未指定负责人' }}
                </span>
                <span class="inline-flex items-center gap-1.5">
                  <span class="material-symbols-outlined text-[16px]">schedule</span>
                  {{ formatDateTime(currentTaskConversation.dueDate) }}
                </span>
              </div>

              <div v-else class="mt-3 flex flex-wrap gap-3 text-sm text-slate-500">
                <span class="inline-flex items-center gap-1.5">
                  <span class="material-symbols-outlined text-[16px]">corporate_fare</span>
                  {{ project.customerName || '未关联客户' }}
                </span>
                <span class="inline-flex items-center gap-1.5">
                  <span class="material-symbols-outlined text-[16px]">person</span>
                  {{ project.ownerName || '未指定负责人' }}
                </span>
                <span class="inline-flex items-center gap-1.5">
                  <span class="material-symbols-outlined text-[16px]">schedule</span>
                  更新于 {{ formatDateTime(project.updateTime) }}
                </span>
              </div>
            </div>

            <div class="flex flex-wrap items-center gap-2">
              <template v-if="isTaskConversation">
                <button
                  type="button"
                  class="rounded-2xl border border-slate-200 bg-white px-4 py-2 text-sm font-semibold text-slate-700 transition-colors hover:bg-slate-50"
                  @click="backToProject"
                >
                  返回项目
                </button>
                <button
                  v-if="currentTaskConversation"
                  type="button"
                  class="rounded-2xl border border-slate-200 bg-white px-4 py-2 text-sm font-semibold text-slate-700 transition-colors hover:bg-slate-50"
                  @click="openTaskDetail(currentTaskConversation)"
                >
                  查看任务详情
                </button>
              </template>
              <template v-else>
                <button
                  v-if="canCreateTask"
                  type="button"
                  class="inline-flex items-center gap-2 rounded-2xl bg-slate-900 px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-slate-700"
                  @click="openCreateTask()"
                >
                  <span class="material-symbols-outlined text-[17px]">add</span>
                  新建任务
                </button>
                <el-dropdown trigger="click" @command="handleProjectMoreAction">
                  <button
                    type="button"
                    class="flex size-10 items-center justify-center rounded-2xl border border-slate-200 bg-white text-slate-600 transition-colors hover:bg-slate-50 hover:text-slate-900"
                    aria-label="更多项目操作"
                  >
                    <span class="material-symbols-outlined text-[20px]">more_horiz</span>
                  </button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item
                        v-if="canEditProject"
                        command="edit-project"
                      >
                        <span class="inline-flex items-center gap-2">
                          <span class="material-symbols-outlined text-[16px]">edit</span>
                          <span>编辑项目</span>
                        </span>
                      </el-dropdown-item>
                      <el-dropdown-item command="members">
                        <span class="inline-flex items-center gap-2">
                          <span class="material-symbols-outlined text-[16px]">group</span>
                          <span>项目成员</span>
                        </span>
                      </el-dropdown-item>
                      <el-dropdown-item
                        v-if="canArchiveProject && project.status !== 'ARCHIVED'"
                        command="archive-project"
                      >
                        <span class="inline-flex items-center gap-2">
                          <span class="material-symbols-outlined text-[16px]">inventory_2</span>
                          <span>归档项目</span>
                        </span>
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
                <div class="flex rounded-2xl border border-slate-200 bg-slate-50 p-1">
                  <button
                    v-for="tab in projectTabs"
                    :key="tab.value"
                    type="button"
                    class="rounded-xl text-sm font-semibold transition-colors"
                    :class="[
                      viewMode === tab.value ? 'bg-white text-primary shadow-sm' : 'text-slate-500 hover:text-slate-700',
                      tab.iconOnly ? 'flex size-9 items-center justify-center p-0' : 'inline-flex items-center gap-1.5 px-4 py-2'
                    ]"
                    :title="tab.label"
                    :aria-label="tab.label"
                    @click="switchProjectView(tab.value)"
                  >
                    <span v-if="tab.icon" class="material-symbols-outlined text-[18px]">{{ tab.icon }}</span>
                    <span v-if="tab.iconOnly" class="sr-only">{{ tab.label }}</span>
                    <span v-else>{{ tab.label }}</span>
                  </button>
                </div>
              </template>
            </div>
          </div>
        </div>
      </header>

      <main class="flex-1 overflow-hidden">
        <div v-if="viewMode === 'ai'" class="mx-auto flex h-full max-w-7xl flex-col overflow-hidden px-4 py-4 md:px-6">
          <section class="flex min-h-0 flex-1 flex-col overflow-hidden rounded-[30px] border border-slate-200 bg-white shadow-sm">
            <div class="border-b border-slate-100 px-5 py-4">
              <p class="text-sm font-bold text-slate-900">项目 AI 对话</p>
              <p class="mt-1 text-sm text-slate-500">
                当前对话对象：项目 - {{ project.name }}。例如可以说“明天给客户汇报需求文档和设计方案，帮我创建一个任务”。
              </p>
            </div>

            <div
              v-if="projectSchedules.length || projectAttachments.length"
              class="border-b border-slate-100 bg-slate-50/60 px-5 py-4"
            >
              <div class="mx-auto grid max-w-4xl gap-3 md:grid-cols-2">
                <div class="rounded-2xl border border-slate-200 bg-white px-4 py-3">
                  <div class="flex items-center justify-between">
                    <p class="text-sm font-bold text-slate-800">项目日程</p>
                    <span class="text-xs font-semibold text-slate-400">{{ projectSchedules.length }} 项</span>
                  </div>
                  <div v-if="projectSchedules.length" class="mt-3 flex flex-col gap-2">
                    <div
                      v-for="item in projectSchedules.slice(0, 3)"
                      :key="item.scheduleId"
                      class="rounded-xl bg-slate-50 px-3 py-2 text-sm text-slate-600"
                    >
                      <p class="font-semibold text-slate-800">{{ item.title }}</p>
                      <p class="mt-1 text-xs text-slate-400">{{ formatDateTime(item.scheduleTime || item.createTime) }}</p>
                    </div>
                  </div>
                  <p v-else class="mt-3 text-sm text-slate-400">暂无项目日程</p>
                </div>

                <div class="rounded-2xl border border-slate-200 bg-white px-4 py-3">
                  <div class="flex items-center justify-between">
                    <p class="text-sm font-bold text-slate-800">项目附件</p>
                    <span class="text-xs font-semibold text-slate-400">{{ projectAttachments.length }} 项</span>
                  </div>
                  <div v-if="projectAttachments.length" class="mt-3 flex flex-col gap-2">
                    <div
                      v-for="item in projectAttachments.slice(0, 3)"
                      :key="item.attachmentId"
                      class="rounded-xl bg-slate-50 px-3 py-2 text-sm text-slate-600"
                    >
                      <p class="font-semibold text-slate-800">{{ item.name }}</p>
                      <p class="mt-1 text-xs text-slate-400">{{ item.createdByName || '项目 AI' }} · {{ formatDateTime(item.createTime) }}</p>
                    </div>
                  </div>
                  <p v-else class="mt-3 text-sm text-slate-400">暂无项目附件</p>
                </div>
              </div>
            </div>

            <div class="flex-1 overflow-y-auto px-5 py-5">
              <div class="mx-auto flex max-w-4xl flex-col gap-4">
                <div
                  v-for="message in project.chatMessages"
                  :key="message.messageId"
                  class="flex"
                  :class="message.role === 'user' ? 'justify-end' : 'justify-start'"
                >
                  <div
                    class="max-w-[88%] whitespace-pre-wrap rounded-[24px] px-4 py-3 text-sm leading-6 shadow-sm"
                    :class="message.role === 'user' ? 'bg-slate-900 text-white' : 'border border-slate-200 bg-slate-50 text-slate-700'"
                  >
                    {{ message.content }}
                  </div>
                </div>
              </div>
            </div>

            <div class="border-t border-slate-100 px-5 py-4">
              <div class="mx-auto max-w-4xl">
                <template v-if="canUseAiChat">
                  <el-input
                    v-model="aiInput"
                    type="textarea"
                    :rows="4"
                    resize="none"
                    class="wk-crm-el-field-input"
                    placeholder="输入项目指令，例如：这个项目和北京百度公司合作，明天下午三点给客户汇报需求文档和设计方案，帮我创建一个任务。"
                    @keydown.enter.exact.prevent="handleSendAiMessage"
                  />
                  <div class="mt-3 flex items-center justify-between gap-3">
                    <p class="text-xs text-slate-400">AI 可创建任务、泳道、项目日程和项目附件，并会在执行前校验项目权限。</p>
                    <button
                      type="button"
                      class="inline-flex items-center gap-2 rounded-2xl bg-primary px-4 py-2.5 text-sm font-bold text-white transition-colors hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-50"
                      :disabled="!aiInput.trim()"
                      @click="handleSendAiMessage"
                    >
                      <span class="material-symbols-outlined text-[16px]">send</span>
                      发送
                    </button>
                  </div>
                </template>
                <div v-else class="rounded-2xl border border-amber-200 bg-amber-50 px-4 py-4 text-sm text-amber-700">
                  当前账号没有使用项目 AI 对话的权限。
                </div>
              </div>
            </div>
          </section>
        </div>

        <div v-else-if="viewMode === 'task_ai' && currentTaskConversation" class="flex h-full overflow-hidden bg-white">
          <section class="flex min-w-0 flex-1 flex-col overflow-hidden">
            <div class="shrink-0 border-b border-slate-100 bg-white px-4 py-3 md:px-8">
              <div class="flex min-w-0 flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
                <div class="min-w-0">
                  <p class="text-xs font-bold uppercase tracking-[0.18em] text-slate-400">当前对话对象：任务</p>
                  <div class="mt-2 flex min-w-0 flex-wrap items-center gap-2">
                    <h2 class="min-w-[120px] max-w-[520px] truncate text-[18px] font-black leading-7 text-[#0d0d0d]">
                      {{ currentTaskConversation.title }}
                    </h2>
                    <span class="rounded-full px-2.5 py-1 text-xs font-bold" :class="projectTaskPriorityClass(currentTaskConversation.priority)">
                      {{ projectTaskPriorityLabel(currentTaskConversation.priority) }}
                    </span>
                    <span class="rounded-full border border-slate-200 bg-white px-2.5 py-1 text-xs font-bold text-slate-600">
                      {{ currentTaskConversation.status }}
                    </span>
                    <span class="rounded-full border border-slate-200 bg-slate-50 px-2.5 py-1 text-xs font-bold text-slate-600">
                      {{ laneName(currentTaskConversation.laneId) }}
                    </span>
                  </div>
                </div>
                <div class="flex flex-wrap items-center gap-2 text-xs font-semibold text-slate-500">
                  <span class="inline-flex items-center gap-1 rounded-full bg-slate-50 px-3 py-1.5">
                    <span class="material-symbols-outlined text-[15px]">person</span>
                    {{ currentTaskConversation.ownerName || '未指定负责人' }}
                  </span>
                  <span class="inline-flex items-center gap-1 rounded-full bg-slate-50 px-3 py-1.5">
                    <span class="material-symbols-outlined text-[15px]">schedule</span>
                    {{ currentTaskConversation.dueDate ? formatDateTime(currentTaskConversation.dueDate) : '未设置截止时间' }}
                  </span>
                  <button
                    v-if="taskInfoPanelCollapsed"
                    type="button"
                    class="inline-flex items-center gap-1 rounded-full border border-slate-200 bg-white px-3 py-1.5 text-slate-600 transition-colors hover:bg-slate-50"
                    @click="taskInfoPanelCollapsed = false"
                  >
                    <span class="material-symbols-outlined text-[15px]">right_panel_open</span>
                    展开信息
                  </button>
                </div>
              </div>
            </div>

            <div class="flex-1 overflow-y-auto bg-[#f8fafc] px-5 py-6">
              <div class="mx-auto flex max-w-4xl flex-col gap-4">
                <div
                  v-for="message in currentTaskConversation.chatMessages"
                  :key="message.messageId"
                  class="flex"
                  :class="message.role === 'user' ? 'justify-end' : 'justify-start'"
                >
                  <div
                    class="max-w-[88%] whitespace-pre-wrap rounded-[24px] px-4 py-3 text-sm leading-6 shadow-sm"
                    :class="message.role === 'user' ? 'bg-[#0d0d0d] text-white' : 'border border-slate-200 bg-white text-slate-700'"
                  >
                    {{ message.content }}
                  </div>
                </div>
                <div v-if="currentTaskConversation.chatMessages.length === 0" class="mx-auto mt-16 max-w-lg text-center text-slate-400">
                  <span class="material-symbols-outlined text-5xl">forum</span>
                  <p class="mt-4 text-sm">围绕这个任务继续沟通，例如修改截止时间、生成执行方案或追加备注。</p>
                </div>
              </div>
            </div>

            <div class="shrink-0 border-t border-slate-100 bg-white px-5 py-4">
              <div class="mx-auto max-w-4xl">
                <template v-if="canUseCurrentTaskAi">
                  <el-input
                    v-model="taskAiInput"
                    type="textarea"
                    :rows="4"
                    resize="none"
                    class="wk-crm-el-field-input"
                    placeholder="输入任务指令，例如：把这个任务改到明天下午三点，或帮我生成一个汇报大纲。"
                    @keydown.enter.exact.prevent="handleSendTaskAiMessage"
                  />
                  <div class="mt-3 flex items-center justify-between gap-3">
                    <p class="text-xs text-slate-400">任务 AI 会基于当前任务上下文修改字段、追加备注、挂载附件或生成执行方案。</p>
                    <button
                      type="button"
                      class="inline-flex items-center gap-2 rounded-2xl bg-primary px-4 py-2.5 text-sm font-bold text-white transition-colors hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-50"
                      :disabled="!taskAiInput.trim()"
                      @click="handleSendTaskAiMessage"
                    >
                      <span class="material-symbols-outlined text-[16px]">send</span>
                      发送
                    </button>
                  </div>
                </template>
                <div v-else class="rounded-2xl border border-amber-200 bg-amber-50 px-4 py-4 text-sm text-amber-700">
                  当前账号没有使用该任务 AI 对话的权限。
                </div>
              </div>
            </div>
          </section>

          <aside
            v-if="!taskInfoPanelCollapsed"
            class="hidden w-[360px] shrink-0 flex-col overflow-hidden border-l border-slate-200 bg-white lg:flex"
          >
            <div class="flex h-14 shrink-0 items-center justify-between border-b border-slate-100 px-5">
              <div>
                <p class="text-sm font-bold text-slate-900">任务基本信息</p>
                <p class="text-xs text-slate-400">可展开/收起</p>
              </div>
              <button
                type="button"
                class="flex size-8 items-center justify-center rounded-xl text-slate-400 transition-colors hover:bg-slate-50 hover:text-slate-700"
                aria-label="收起任务信息"
                @click="taskInfoPanelCollapsed = true"
              >
                <span class="material-symbols-outlined text-[18px]">right_panel_close</span>
              </button>
            </div>

            <div class="min-h-0 flex-1 overflow-y-auto px-5 py-5">
              <div class="space-y-4">
                <div class="rounded-3xl border border-slate-200 bg-slate-50 p-4">
                  <p class="text-xs font-bold uppercase tracking-[0.18em] text-slate-400">所属项目</p>
                  <p class="mt-2 text-sm font-semibold text-slate-900">{{ project.name }}</p>
                </div>

                <dl class="grid grid-cols-1 gap-3 text-sm">
                  <div class="rounded-2xl border border-slate-200 px-4 py-3">
                    <dt class="text-xs text-slate-400">任务描述</dt>
                    <dd class="mt-2 leading-6 text-slate-700">{{ currentTaskConversation.description || '暂无描述' }}</dd>
                  </div>
                  <div class="rounded-2xl border border-slate-200 px-4 py-3">
                    <dt class="text-xs text-slate-400">任务状态 / 泳道</dt>
                    <dd class="mt-2 font-semibold text-slate-800">{{ currentTaskConversation.status }} / {{ laneName(currentTaskConversation.laneId) }}</dd>
                  </div>
                  <div class="rounded-2xl border border-slate-200 px-4 py-3">
                    <dt class="text-xs text-slate-400">关联客户</dt>
                    <dd class="mt-2 font-semibold text-slate-800">{{ currentTaskConversation.customerName || project.customerName || '未关联客户' }}</dd>
                  </div>
                  <div class="rounded-2xl border border-slate-200 px-4 py-3">
                    <dt class="text-xs text-slate-400">参与人</dt>
                    <dd class="mt-2 leading-6 text-slate-700">
                      {{ currentTaskConversation.participantNames?.length ? currentTaskConversation.participantNames.join('、') : '暂无参与人' }}
                    </dd>
                  </div>
                  <div class="rounded-2xl border border-slate-200 px-4 py-3">
                    <dt class="text-xs text-slate-400">附件 / 日程</dt>
                    <dd class="mt-2 flex items-center gap-3 text-slate-700">
                      <span :class="currentTaskConversation.hasAttachments ? 'text-primary' : 'text-slate-400'" class="inline-flex items-center gap-1">
                        <span class="material-symbols-outlined text-[16px]">attach_file</span>
                        {{ currentTaskConversation.attachments.length || (currentTaskConversation.hasAttachments ? 1 : 0) }}
                      </span>
                      <span :class="currentTaskConversation.hasSchedule ? 'text-emerald-600' : 'text-slate-400'" class="inline-flex items-center gap-1">
                        <span class="material-symbols-outlined text-[16px]">calendar_month</span>
                        {{ currentTaskConversation.schedules.length || (currentTaskConversation.hasSchedule ? 1 : 0) }}
                      </span>
                    </dd>
                  </div>
                  <div class="rounded-2xl border border-slate-200 px-4 py-3">
                    <dt class="text-xs text-slate-400">创建 / 更新</dt>
                    <dd class="mt-2 space-y-1 text-slate-700">
                      <p>创建：{{ formatDateTime(currentTaskConversation.createTime) }}</p>
                      <p>更新：{{ formatDateTime(currentTaskConversation.updateTime) }}</p>
                    </dd>
                  </div>
                  <div class="rounded-2xl border border-slate-200 px-4 py-3">
                    <dt class="text-xs text-slate-400">AI 生成来源</dt>
                    <dd class="mt-2 leading-6 text-slate-700">
                      {{ currentTaskConversation.generatedByAi ? (currentTaskConversation.aiSourceText || 'AI 对话创建') : '手动创建' }}
                    </dd>
                  </div>
                </dl>
              </div>
            </div>

            <div class="shrink-0 border-t border-slate-100 p-4">
              <button
                v-if="canEditTask(currentTaskConversation)"
                type="button"
                class="w-full rounded-2xl bg-slate-900 px-4 py-2.5 text-sm font-bold text-white transition-colors hover:bg-slate-700"
                @click="handleEditTask(currentTaskConversation)"
              >
                编辑任务
              </button>
            </div>
          </aside>
        </div>

        <div v-else-if="viewMode === 'board'" class="h-full overflow-hidden px-4 py-4 md:px-6">
          <div class="mx-auto flex h-full max-w-7xl flex-col overflow-hidden rounded-[30px] border border-slate-200 bg-white shadow-sm">
            <div class="flex-1 overflow-x-auto overflow-y-hidden px-5 py-5">
              <div class="flex h-full min-w-max gap-4">
                <section
                  v-for="lane in orderedLanes"
                  :key="lane.laneId"
                  class="flex h-full min-h-0 w-[320px] flex-col rounded-[28px] border border-slate-200 bg-[#f8f8f6] p-3"
                  @dragover.prevent="handleLaneDragOver(lane.laneId, $event)"
                  @drop.prevent="handleLaneDrop(lane.laneId)"
                >
                  <header class="mb-3 flex items-start justify-between gap-2 px-2">
                    <div>
                      <div class="flex items-center gap-2">
                        <h2 class="text-sm font-bold text-slate-900">{{ lane.name }}</h2>
                        <span class="inline-flex size-5 items-center justify-center rounded-full bg-white text-[10px] font-bold text-slate-500">
                          {{ tasksByLane(lane.laneId).length }}
                        </span>
                      </div>
                      <p class="mt-1 text-xs text-slate-400">{{ lane.system ? '系统默认泳道' : '自定义泳道' }}</p>
                    </div>
                    <div class="flex items-center gap-1">
                      <button
                        v-if="canCreateTask"
                        type="button"
                        class="flex size-8 items-center justify-center rounded-xl text-slate-400 transition-colors hover:bg-white hover:text-slate-700"
                        @click="openCreateTask(lane.laneId)"
                      >
                        <span class="material-symbols-outlined text-[18px]">add</span>
                      </button>
                      <button
                        v-if="canEditLane"
                        type="button"
                        class="flex size-8 items-center justify-center rounded-xl text-slate-400 transition-colors hover:bg-white hover:text-slate-700"
                        @click="promptRenameLane(lane.laneId)"
                      >
                        <span class="material-symbols-outlined text-[18px]">edit</span>
                      </button>
                      <button
                        v-if="canDeleteLane && !lane.system"
                        type="button"
                        class="flex size-8 items-center justify-center rounded-xl text-slate-400 transition-colors hover:bg-white hover:text-red-500"
                        @click="handleDeleteLane(lane.laneId)"
                      >
                        <span class="material-symbols-outlined text-[18px]">delete</span>
                      </button>
                    </div>
                  </header>

                  <div class="flex min-h-0 flex-1 flex-col gap-3 overflow-y-auto px-1 pb-1">
                    <article
                      v-for="task in tasksByLane(lane.laneId)"
                      :key="task.taskId"
                      :draggable="canMoveTask(task)"
                      class="cursor-pointer rounded-[24px] border border-slate-200 bg-white p-4 shadow-sm transition-all hover:border-primary/30 hover:shadow-md"
                      :class="[
                        draggingTaskId === task.taskId ? 'opacity-60 ring-1 ring-primary/20' : '',
                        canMoveTask(task) ? 'cursor-grab active:cursor-grabbing' : ''
                      ]"
                      @click="enterTaskConversation(task)"
                      @dragstart="handleTaskDragStart(task.taskId, $event)"
                      @dragend="handleTaskDragEnd"
                    >
                      <div class="flex items-start justify-between gap-3">
                        <h3 class="line-clamp-2 text-sm font-bold leading-6 text-slate-900">{{ task.title }}</h3>
                        <span class="shrink-0 rounded-full px-2 py-1 text-xs font-bold" :class="projectTaskPriorityClass(task.priority)">
                          {{ projectTaskPriorityLabel(task.priority) }}
                        </span>
                      </div>

                      <div class="mt-3 space-y-2 text-xs text-slate-500">
                        <p class="inline-flex items-center gap-1.5">
                          <span class="material-symbols-outlined text-[14px]">schedule</span>
                          {{ task.dueDate ? formatDateTime(task.dueDate) : '未设置截止时间' }}
                        </p>
                        <p class="inline-flex items-center gap-1.5">
                          <span class="material-symbols-outlined text-[14px]">person</span>
                          {{ task.ownerName || '未指定负责人' }}
                        </p>
                        <p class="inline-flex items-center gap-1.5">
                          <span class="material-symbols-outlined text-[14px]">group</span>
                          {{ task.participantNames?.length ? task.participantNames.join('、') : '无参与人' }}
                        </p>
                        <p class="inline-flex items-center gap-1.5">
                          <span class="material-symbols-outlined text-[14px]">corporate_fare</span>
                          {{ task.customerName || project.customerName || '未关联客户' }}
                        </p>
                      </div>

                      <div class="mt-4 flex items-center justify-between">
                        <div class="flex items-center gap-2 text-slate-400">
                          <span
                            class="inline-flex items-center gap-1 rounded-full border border-slate-200 px-2 py-1"
                            :class="task.hasAttachments ? 'text-primary' : ''"
                          >
                            <span class="material-symbols-outlined text-[14px]">attach_file</span>
                            附件
                          </span>
                          <span
                            class="inline-flex items-center gap-1 rounded-full border border-slate-200 px-2 py-1"
                            :class="task.hasSchedule ? 'text-emerald-600' : ''"
                          >
                            <span class="material-symbols-outlined text-[14px]">calendar_month</span>
                            日程
                          </span>
                        </div>
                        <span v-if="task.generatedByAi" class="inline-flex items-center gap-1 text-primary">
                          <span class="material-symbols-outlined text-[14px]">auto_awesome</span>
                          AI
                        </span>
                      </div>
                    </article>

                    <div
                      class="flex min-h-28 flex-col items-center justify-center rounded-[22px] border border-dashed text-center text-sm"
                      :class="dragOverLaneId === lane.laneId ? 'border-primary bg-primary/5 text-primary' : 'border-slate-200 bg-white/70 text-slate-400'"
                    >
                      <span class="material-symbols-outlined mb-2 text-2xl">move_to_inbox</span>
                      <p>{{ dragOverLaneId === lane.laneId ? '松开即可移动到该泳道' : '拖动任务到这里切换状态' }}</p>
                    </div>
                  </div>
                </section>

                <button
                  v-if="canAddLane"
                  type="button"
                  class="group flex h-full min-h-[420px] w-[260px] shrink-0 flex-col items-center justify-center rounded-[28px] border border-dashed border-slate-300 bg-white/70 p-6 text-center text-slate-400 transition-all hover:border-primary/40 hover:bg-primary/5 hover:text-primary"
                  @click="promptAddLane"
                >
                  <span class="material-symbols-outlined text-4xl transition-transform group-hover:scale-110">add</span>
                  <span class="mt-3 text-sm font-bold">新建泳道</span>
                  <span class="mt-1 text-xs">点击在项目中新增一列</span>
                </button>
              </div>
            </div>
          </div>
        </div>

        <div v-else-if="viewMode === 'list'" class="h-full overflow-y-auto px-4 py-4 md:px-6">
          <div class="mx-auto flex max-w-7xl flex-col overflow-hidden rounded-[30px] border border-slate-200 bg-white shadow-sm">
            <div v-if="visibleProjectTasks.length === 0" class="px-6 py-20 text-center text-slate-400">
              <span class="material-symbols-outlined text-5xl">task_alt</span>
              <p class="mt-4 text-sm">当前项目还没有可查看的任务。</p>
            </div>

            <div v-else class="overflow-x-auto">
              <table class="min-w-full divide-y divide-slate-100 text-sm">
                <thead class="bg-slate-50 text-left text-slate-500">
                  <tr>
                    <th class="px-5 py-3 font-semibold">任务名称</th>
                    <th class="px-5 py-3 font-semibold">泳道</th>
                    <th class="px-5 py-3 font-semibold">优先级</th>
                    <th class="px-5 py-3 font-semibold">负责人</th>
                    <th class="px-5 py-3 font-semibold">截止时间</th>
                    <th class="px-5 py-3 font-semibold">关联客户</th>
                    <th class="px-5 py-3 font-semibold">资料</th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-slate-100">
                  <tr
                    v-for="task in visibleProjectTasks"
                    :key="task.taskId"
                    class="cursor-pointer transition-colors hover:bg-slate-50"
                    @click="enterTaskConversation(task)"
                  >
                    <td class="px-5 py-4">
                      <p class="font-semibold text-slate-900">{{ task.title }}</p>
                      <p v-if="task.description" class="mt-1 line-clamp-1 text-xs text-slate-400">{{ task.description }}</p>
                    </td>
                    <td class="px-5 py-4 text-slate-600">{{ laneName(task.laneId) }}</td>
                    <td class="px-5 py-4">
                      <span class="rounded-full px-2.5 py-1 text-xs font-bold" :class="projectTaskPriorityClass(task.priority)">
                        {{ projectTaskPriorityLabel(task.priority) }}
                      </span>
                    </td>
                    <td class="px-5 py-4 text-slate-600">{{ task.ownerName || '未指定' }}</td>
                    <td class="px-5 py-4 text-slate-600">{{ task.dueDate ? formatDateTime(task.dueDate) : '未设置' }}</td>
                    <td class="px-5 py-4 text-slate-600">{{ task.customerName || project.customerName || '未关联' }}</td>
                    <td class="px-5 py-4 text-slate-500">
                      <div class="flex items-center gap-2">
                        <span class="inline-flex items-center gap-1" :class="task.hasAttachments ? 'text-primary' : 'text-slate-300'">
                          <span class="material-symbols-outlined text-[15px]">attach_file</span>
                        </span>
                        <span class="inline-flex items-center gap-1" :class="task.hasSchedule ? 'text-emerald-600' : 'text-slate-300'">
                          <span class="material-symbols-outlined text-[15px]">calendar_month</span>
                        </span>
                        <span v-if="task.generatedByAi" class="inline-flex items-center gap-1 text-primary">
                          <span class="material-symbols-outlined text-[15px]">auto_awesome</span>
                        </span>
                      </div>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>

        <div v-else-if="viewMode === 'cards'" class="h-full overflow-y-auto px-4 py-4 md:px-6">
          <div class="mx-auto flex max-w-7xl flex-col gap-4">
            <div v-if="visibleProjectTasks.length === 0" class="rounded-[30px] border border-slate-200 bg-white px-6 py-20 text-center text-slate-400 shadow-sm">
              <span class="material-symbols-outlined text-5xl">view_cozy</span>
              <p class="mt-4 text-sm">当前项目还没有可查看的任务。</p>
            </div>

            <div v-else class="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
              <article
                v-for="task in visibleProjectTasks"
                :key="task.taskId"
                class="group cursor-pointer rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm transition-all hover:-translate-y-0.5 hover:border-primary/30 hover:shadow-lg hover:shadow-slate-200/60"
                @click="enterTaskConversation(task)"
              >
                <div class="flex items-start justify-between gap-3">
                  <div class="min-w-0">
                    <h3 class="line-clamp-2 text-base font-bold leading-6 text-slate-900 transition-colors group-hover:text-primary">
                      {{ task.title }}
                    </h3>
                    <p class="mt-2 text-xs text-slate-400">{{ laneName(task.laneId) }} · {{ task.status }}</p>
                  </div>
                  <span class="shrink-0 rounded-full px-2.5 py-1 text-xs font-bold" :class="projectTaskPriorityClass(task.priority)">
                    {{ projectTaskPriorityLabel(task.priority) }}
                  </span>
                </div>
                <p class="mt-4 line-clamp-3 min-h-[4.5rem] text-sm leading-6 text-slate-500">
                  {{ task.description || '暂无任务描述，可进入任务对话继续补充资料和执行方案。' }}
                </p>
                <div class="mt-5 grid grid-cols-2 gap-2 text-xs text-slate-500">
                  <div class="rounded-2xl bg-slate-50 px-3 py-2">
                    <p class="text-slate-400">负责人</p>
                    <p class="mt-1 truncate font-semibold text-slate-800">{{ task.ownerName || '未指定' }}</p>
                  </div>
                  <div class="rounded-2xl bg-slate-50 px-3 py-2">
                    <p class="text-slate-400">截止时间</p>
                    <p class="mt-1 truncate font-semibold text-slate-800">{{ task.dueDate ? formatDateTime(task.dueDate) : '未设置' }}</p>
                  </div>
                </div>
                <div class="mt-4 flex items-center justify-between text-xs text-slate-400">
                  <span class="truncate">{{ task.customerName || project.customerName || '未关联客户' }}</span>
                  <span class="inline-flex items-center gap-2">
                    <span :class="task.hasAttachments ? 'text-primary' : ''" class="material-symbols-outlined text-[15px]">attach_file</span>
                    <span :class="task.hasSchedule ? 'text-emerald-600' : ''" class="material-symbols-outlined text-[15px]">calendar_month</span>
                    <span v-if="task.generatedByAi" class="material-symbols-outlined text-[15px] text-primary">auto_awesome</span>
                  </span>
                </div>
              </article>
            </div>
          </div>
        </div>

        <div v-else class="h-full overflow-y-auto px-4 py-4 md:px-6">
          <div class="mx-auto flex max-w-7xl flex-col gap-4">
            <section class="grid grid-cols-1 gap-4 md:grid-cols-3">
              <article class="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
                <p class="text-sm text-slate-500">项目成员</p>
                <p class="mt-3 text-3xl font-black text-slate-900">{{ project.members.length }}</p>
              </article>
              <article class="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
                <p class="text-sm text-slate-500">正常成员</p>
                <p class="mt-3 text-3xl font-black text-slate-900">{{ activeMembers.length }}</p>
              </article>
              <article class="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
                <p class="text-sm text-slate-500">成员变更日志</p>
                <p class="mt-3 text-3xl font-black text-slate-900">{{ project.memberLogs.length }}</p>
              </article>
            </section>

            <section class="rounded-[28px] border border-slate-200 bg-white shadow-sm">
              <div class="flex items-center justify-between border-b border-slate-100 px-5 py-4 md:px-6">
                <div>
                  <h2 class="text-lg font-bold text-slate-900">项目成员</h2>
                  <p class="mt-1 text-sm text-slate-500">按角色和权限控制项目可见性、任务协作和 AI 使用范围。</p>
                </div>
                <button
                  v-if="canAddMember"
                  type="button"
                  class="rounded-2xl bg-slate-900 px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-slate-700"
                  @click="openAddMember"
                >
                  添加成员
                </button>
              </div>

              <div class="overflow-x-auto">
                <table class="min-w-full divide-y divide-slate-100 text-sm">
                  <thead class="bg-slate-50 text-left text-slate-500">
                    <tr>
                      <th class="px-5 py-3 font-semibold">成员姓名</th>
                      <th class="px-5 py-3 font-semibold">所属部门</th>
                      <th class="px-5 py-3 font-semibold">项目角色</th>
                      <th class="px-5 py-3 font-semibold">权限范围</th>
                      <th class="px-5 py-3 font-semibold">加入时间</th>
                      <th class="px-5 py-3 font-semibold">最近操作时间</th>
                      <th class="px-5 py-3 font-semibold">状态</th>
                      <th class="px-5 py-3 font-semibold text-right">操作</th>
                    </tr>
                  </thead>
                  <tbody class="divide-y divide-slate-100">
                    <tr v-for="member in memberRows" :key="member.memberId" class="align-top">
                      <td class="px-5 py-4">
                        <p class="font-semibold text-slate-900">{{ member.memberName }}</p>
                        <p class="mt-1 text-xs text-slate-400">{{ member.account }}</p>
                      </td>
                      <td class="px-5 py-4 text-slate-600">{{ member.deptName || '-' }}</td>
                      <td class="px-5 py-4 text-slate-600">{{ projectRoleLabel(member.role) }}</td>
                      <td class="px-5 py-4 text-slate-600">
                        <p class="max-w-md leading-6">{{ memberPermissionSummary(member) }}</p>
                      </td>
                      <td class="px-5 py-4 text-slate-600">{{ formatDateTime(member.joinedAt) }}</td>
                      <td class="px-5 py-4 text-slate-600">{{ formatDateTime(member.lastActionTime) }}</td>
                      <td class="px-5 py-4">
                        <span class="inline-flex rounded-full px-2.5 py-1 text-xs font-bold" :class="projectMemberStatusClass(member.status)">
                          {{ projectMemberStatusLabel(member.status) }}
                        </span>
                      </td>
                      <td class="px-5 py-4">
                        <div class="flex justify-end gap-2">
                          <button
                            v-if="canModifyMemberPermission && member.role !== 'OWNER'"
                            type="button"
                            class="rounded-xl border border-slate-200 px-3 py-1.5 text-xs font-semibold text-slate-700 transition-colors hover:bg-slate-50"
                            @click="openEditMember(member)"
                          >
                            修改角色
                          </button>
                          <button
                            v-if="canModifyMemberPermission && member.role !== 'OWNER'"
                            type="button"
                            class="rounded-xl border border-slate-200 px-3 py-1.5 text-xs font-semibold text-slate-700 transition-colors hover:bg-slate-50"
                            @click="openEditMember(member)"
                          >
                            修改权限
                          </button>
                          <button
                            v-if="canModifyMemberPermission && member.role !== 'OWNER'"
                            type="button"
                            class="rounded-xl border border-slate-200 px-3 py-1.5 text-xs font-semibold text-slate-700 transition-colors hover:bg-slate-50"
                            @click="toggleMemberStatus(member)"
                          >
                            {{ member.status === 'DISABLED' ? '启用成员' : '停用成员' }}
                          </button>
                          <button
                            v-if="canRemoveMember && member.role !== 'OWNER' && member.status !== 'REMOVED'"
                            type="button"
                            class="rounded-xl bg-red-50 px-3 py-1.5 text-xs font-semibold text-red-600 transition-colors hover:bg-red-100"
                            @click="removeMember(member)"
                          >
                            移除成员
                          </button>
                        </div>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </section>

            <section class="rounded-[28px] border border-slate-200 bg-white shadow-sm">
              <div class="border-b border-slate-100 px-5 py-4 md:px-6">
                <h2 class="text-lg font-bold text-slate-900">成员变更记录</h2>
                <p class="mt-1 text-sm text-slate-500">记录添加成员、移除成员、修改角色、修改权限和状态变更。</p>
              </div>
              <div v-if="project.memberLogs.length === 0" class="px-6 py-14 text-center text-slate-400">
                还没有成员变更记录
              </div>
              <div v-else class="divide-y divide-slate-100">
                <article v-for="log in project.memberLogs" :key="log.logId" class="px-5 py-4 md:px-6">
                  <div class="flex flex-col gap-2 md:flex-row md:items-start md:justify-between">
                    <div>
                      <p class="text-sm font-semibold text-slate-900">{{ log.operatorName }} 执行了 {{ memberActionLabel(log.actionType) }}</p>
                      <p class="mt-1 text-sm text-slate-500">目标成员：{{ log.targetUserName }}</p>
                      <p v-if="log.beforeSummary" class="mt-2 text-xs text-slate-400">变更前：{{ log.beforeSummary }}</p>
                      <p v-if="log.afterSummary" class="mt-1 text-xs text-slate-400">变更后：{{ log.afterSummary }}</p>
                    </div>
                    <p class="text-xs text-slate-400">{{ formatDateTime(log.createTime) }}</p>
                  </div>
                </article>
              </div>
            </section>
          </div>
        </div>
      </main>
    </div>

    <div v-else class="flex flex-1 items-center justify-center px-6 text-center">
      <div>
        <span class="material-symbols-outlined text-5xl text-slate-300">folder_off</span>
        <p class="mt-4 text-sm text-slate-500">
          {{ project ? '当前账号没有查看该项目的权限。' : '项目不存在或已被删除。' }}
        </p>
        <button
          type="button"
          class="mt-4 rounded-2xl bg-slate-900 px-4 py-2 text-sm font-semibold text-white"
          @click="router.push({ name: 'ProjectList' })"
        >
          返回项目列表
        </button>
      </div>
    </div>

    <ProjectUpsertDialog
      v-model="showProjectDialog"
      :editing-project="project"
      @submit="handleUpdateProject"
    />

    <ProjectTaskDialog
      v-model="showTaskDialog"
      :editing-task="editingTask"
      :lanes="orderedLanes"
      :default-lane-id="defaultLaneId"
      :default-customer-id="project?.customerId"
      :default-customer-name="project?.customerName"
      @submit="handleSubmitTask"
    />

    <ProjectTaskDrawer
      v-model="showTaskDrawer"
      :task="selectedTask"
      :project-name="project?.name"
      :is-mobile="isMobile"
      :can-edit="selectedTask ? canEditTask(selectedTask) : false"
      :can-delete="selectedTask ? canDeleteTask(selectedTask) : false"
      @enter-chat="enterTaskConversation"
      @edit="handleEditTask"
      @delete="handleDeleteTask"
    />

    <ProjectMemberDialog
      v-model="showMemberDialog"
      :editing-member="editingMember"
      @submit="handleSubmitMember"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useResponsive } from '@/composables/useResponsive'
import { useProjectStore } from '@/stores/project'
import type {
  ProjectMember,
  ProjectPermission,
  ProjectStatus,
  ProjectTask,
  ProjectViewMode
} from '@/types/project'
import {
  formatDateTime,
  memberPermissionSummary,
  projectMemberStatusClass,
  projectMemberStatusLabel,
  projectRoleLabel,
  projectStatusClass,
  projectStatusLabel,
  projectTaskPriorityClass,
  projectTaskPriorityLabel
} from '@/utils/project'
import ProjectMemberDialog from '@/views/project/components/ProjectMemberDialog.vue'
import ProjectTaskDialog from '@/views/project/components/ProjectTaskDialog.vue'
import ProjectTaskDrawer from '@/views/project/components/ProjectTaskDrawer.vue'
import ProjectUpsertDialog from '@/views/project/components/ProjectUpsertDialog.vue'

const route = useRoute()
const router = useRouter()
const { isMobile } = useResponsive()
const projectStore = useProjectStore()

type ProjectTab = {
  value: Exclude<ProjectViewMode, 'task_ai'>
  label: string
  icon?: string
  iconOnly?: boolean
}

const projectTabs: ProjectTab[] = [
  { value: 'board', label: '泳道视图', icon: 'view_kanban', iconOnly: true },
  { value: 'list', label: '列表视图', icon: 'view_list', iconOnly: true },
  { value: 'cards', label: '卡片视图', icon: 'view_module', iconOnly: true },
  { value: 'ai', label: 'AI 对话' },
  { value: 'members', label: '项目成员' }
]

const viewMode = ref<ProjectViewMode>('board')
const lastProjectViewMode = ref<Exclude<ProjectViewMode, 'task_ai'>>('board')
const aiInput = ref('')
const taskAiInput = ref('')
const showProjectDialog = ref(false)
const showTaskDialog = ref(false)
const showTaskDrawer = ref(false)
const showMemberDialog = ref(false)
const draggingTaskId = ref('')
const dragOverLaneId = ref('')
const defaultLaneId = ref('')
const selectedTaskId = ref('')
const editingTask = ref<ProjectTask | null>(null)
const editingMember = ref<ProjectMember | null>(null)
const taskConversationId = ref('')
const taskInfoPanelCollapsed = ref(false)

const projectViewValues = new Set<Exclude<ProjectViewMode, 'task_ai'>>(['board', 'list', 'cards', 'ai', 'members'])

function resolveProjectViewFromRoute(): Exclude<ProjectViewMode, 'task_ai'> {
  const raw = route.query.view
  const value = typeof raw === 'string' ? raw : Array.isArray(raw) ? raw[0] : ''
  return projectViewValues.has(value as Exclude<ProjectViewMode, 'task_ai'>)
    ? value as Exclude<ProjectViewMode, 'task_ai'>
    : 'board'
}

onMounted(async () => {
  await projectStore.ensureInitialized()
  if (projectId.value) {
    await projectStore.loadProject(projectId.value)
  }
  syncTaskConversationFromRoute()
})

watch(() => [route.query.taskId, route.query.view], () => {
  syncTaskConversationFromRoute()
})

const projectId = computed(() => String(route.params.id || ''))
const project = computed(() => projectStore.getProjectById(projectId.value))
const projectAttachments = computed(() => project.value?.attachments || [])
const projectSchedules = computed(() => project.value?.schedules || [])
const canViewProject = computed(() => projectStore.getUserProjectPermission(projectId.value, 'VIEW_PROJECT'))
const orderedLanes = computed(() =>
  [...(project.value?.lanes || [])].sort((a, b) => a.order - b.order)
)
const activeMembers = computed(() => (project.value?.members || []).filter(member => member.status === 'ACTIVE'))
const memberRows = computed(() =>
  [...(project.value?.members || [])].sort((a, b) => new Date(b.lastActionTime).getTime() - new Date(a.lastActionTime).getTime())
)
const visibleProjectTasks = computed(() =>
  (project.value?.tasks || [])
    .filter(task => projectStore.canCurrentUserViewTask(projectId.value, task))
    .sort((a, b) => new Date(b.updateTime).getTime() - new Date(a.updateTime).getTime())
)

const canEditProject = computed(() => projectStore.getUserProjectPermission(projectId.value, 'EDIT_PROJECT'))
const canArchiveProject = computed(() => projectStore.getUserProjectPermission(projectId.value, 'ARCHIVE_PROJECT'))
const canUseAiChat = computed(() => projectStore.getUserProjectPermission(projectId.value, 'USE_AI_CHAT'))
const canCreateTask = computed(() => projectStore.getUserProjectPermission(projectId.value, 'CREATE_TASK'))
const canDeleteTaskPermission = computed(() => projectStore.getUserProjectPermission(projectId.value, 'DELETE_TASK'))
const canAddLane = computed(() => projectStore.getUserProjectPermission(projectId.value, 'ADD_LANE'))
const canEditLane = computed(() => projectStore.getUserProjectPermission(projectId.value, 'EDIT_LANE'))
const canDeleteLane = computed(() => projectStore.getUserProjectPermission(projectId.value, 'DELETE_LANE'))
const canAddMember = computed(() => projectStore.getUserProjectPermission(projectId.value, 'ADD_MEMBER'))
const canRemoveMember = computed(() => projectStore.getUserProjectPermission(projectId.value, 'REMOVE_MEMBER'))
const canModifyMemberPermission = computed(() => projectStore.getUserProjectPermission(projectId.value, 'MODIFY_MEMBER_PERMISSION'))

const selectedTask = computed(() =>
  project.value?.tasks.find(task => task.taskId === selectedTaskId.value) || null
)
const currentTaskConversation = computed(() =>
  project.value?.tasks.find(task => task.taskId === taskConversationId.value) || null
)
const isTaskConversation = computed(() => viewMode.value === 'task_ai')
const canUseCurrentTaskAi = computed(() =>
  currentTaskConversation.value ? projectStore.canCurrentUserUseTaskAi(projectId.value, currentTaskConversation.value) : false
)

watch(currentTaskConversation, task => {
  if (!task && viewMode.value === 'task_ai') {
    backToProject()
  }
})

function syncTaskConversationFromRoute() {
  const routeTaskId = typeof route.query.taskId === 'string' ? route.query.taskId : ''
  taskConversationId.value = routeTaskId
  if (routeTaskId) {
    viewMode.value = 'task_ai'
  } else if (viewMode.value === 'task_ai') {
    viewMode.value = resolveProjectViewFromRoute()
    lastProjectViewMode.value = viewMode.value
  } else {
    viewMode.value = resolveProjectViewFromRoute()
    lastProjectViewMode.value = viewMode.value
  }
}

function switchProjectView(mode: Exclude<ProjectViewMode, 'task_ai'>) {
  lastProjectViewMode.value = mode
  viewMode.value = mode
  if (taskConversationId.value) {
    taskConversationId.value = ''
    router.replace({ query: { ...route.query, taskId: undefined, view: mode } })
    return
  }
  router.replace({ query: { ...route.query, view: mode } })
}

function handleProjectMoreAction(command: string | number) {
  const action = String(command)
  if (action === 'edit-project') {
    openEditProject()
    return
  }
  if (action === 'members') {
    switchProjectView('members')
    return
  }
  if (action === 'archive-project') {
    void handleArchiveProject()
  }
}

function handleBackClick() {
  if (isTaskConversation.value) {
    backToProject()
    return
  }
  router.push({ name: 'ProjectList' })
}

function backToProject() {
  viewMode.value = lastProjectViewMode.value
  taskConversationId.value = ''
  taskAiInput.value = ''
  router.replace({ query: { ...route.query, taskId: undefined, view: lastProjectViewMode.value } })
}

function tasksByLane(laneId: string) {
  return visibleProjectTasks.value
    .filter(task => task.laneId === laneId)
    .sort((a, b) => new Date(b.updateTime).getTime() - new Date(a.updateTime).getTime())
}

function laneName(laneId: string) {
  return orderedLanes.value.find(lane => lane.laneId === laneId)?.name || '未分配泳道'
}

function canMoveTask(task: ProjectTask) {
  return projectStore.canCurrentUserMoveTask(projectId.value, task)
}

function canEditTask(task: ProjectTask) {
  return projectStore.canCurrentUserEditTask(projectId.value, task)
}

function canDeleteTask(task: ProjectTask) {
  return canDeleteTaskPermission.value && canEditTask(task)
}

function openEditProject() {
  if (!canEditProject.value) return
  showProjectDialog.value = true
}

async function handleUpdateProject(payload: {
  name: string
  description?: string
  customerId?: string
  customerName?: string
  ownerId?: string
  ownerName?: string
  startDate?: string
  dueDate?: string
  status: ProjectStatus
}) {
  if (!project.value || !canEditProject.value) return
  await projectStore.updateProject({
    projectId: project.value.projectId,
    ...payload
  })
  ElMessage.success('项目信息已更新')
}

async function handleArchiveProject() {
  if (!project.value || !canArchiveProject.value) return
  try {
    await ElMessageBox.confirm(`确定归档项目“${project.value.name}”吗？`, '提示', { type: 'warning' })
    await projectStore.archiveProject(project.value.projectId)
    ElMessage.success('项目已归档')
  } catch {
    // noop
  }
}

function openCreateTask(laneId?: string) {
  if (!canCreateTask.value) return
  editingTask.value = null
  defaultLaneId.value = laneId || orderedLanes.value[0]?.laneId || ''
  showTaskDialog.value = true
}

async function handleSubmitTask(payload: {
  title: string
  description?: string
  laneId: string
  dueDate?: string
  ownerId?: string
  ownerName?: string
  participantIds?: string[]
  participantNames?: string[]
  priority: ProjectTask['priority']
  customerId?: string
  customerName?: string
  hasAttachments: boolean
  hasSchedule: boolean
}) {
  if (!project.value) return
  if (editingTask.value) {
    if (!canEditTask(editingTask.value)) return
    await projectStore.updateTask(project.value.projectId, {
      taskId: editingTask.value.taskId,
      ...payload
    })
    ElMessage.success('任务已更新')
  } else {
    if (!canCreateTask.value) return
    await projectStore.createTask(project.value.projectId, payload)
    ElMessage.success('任务创建成功')
  }
}

function openTaskDetail(task: ProjectTask) {
  if (!projectStore.canCurrentUserViewTask(projectId.value, task)) return
  selectedTaskId.value = task.taskId
  showTaskDrawer.value = true
}

function enterTaskConversation(task: ProjectTask) {
  if (!projectStore.canCurrentUserUseTaskAi(projectId.value, task)) {
    ElMessage.warning('当前账号没有使用该任务 AI 对话的权限')
    return
  }
  showTaskDrawer.value = false
  selectedTaskId.value = task.taskId
  taskConversationId.value = task.taskId
  if (viewMode.value !== 'task_ai') {
    lastProjectViewMode.value = viewMode.value as Exclude<ProjectViewMode, 'task_ai'>
  }
  viewMode.value = 'task_ai'
  router.replace({ query: { ...route.query, taskId: task.taskId } })
}

function handleEditTask(task: ProjectTask) {
  if (!canEditTask(task)) return
  editingTask.value = task
  showTaskDrawer.value = false
  showTaskDialog.value = true
}

async function handleDeleteTask(task: ProjectTask) {
  if (!project.value || !canDeleteTask(task)) return
  try {
    await ElMessageBox.confirm(`确定删除任务“${task.title}”吗？`, '提示', { type: 'warning' })
    await projectStore.deleteTask(project.value.projectId, task.taskId)
    showTaskDrawer.value = false
    if (taskConversationId.value === task.taskId) {
      backToProject()
    }
    if (selectedTaskId.value === task.taskId) {
      selectedTaskId.value = ''
    }
    ElMessage.success('任务已删除')
  } catch {
    // noop
  }
}

function handleTaskDragStart(taskId: string, event: DragEvent) {
  const task = project.value?.tasks.find(item => item.taskId === taskId)
  if (!task || !canMoveTask(task)) {
    event.preventDefault()
    return
  }
  draggingTaskId.value = taskId
  event.dataTransfer?.setData('text/plain', taskId)
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
  }
}

function handleTaskDragEnd() {
  draggingTaskId.value = ''
  dragOverLaneId.value = ''
}

function handleLaneDragOver(laneId: string, event: DragEvent) {
  const task = project.value?.tasks.find(item => item.taskId === draggingTaskId.value)
  if (!task || !canMoveTask(task)) return
  dragOverLaneId.value = laneId
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'move'
  }
}

async function handleLaneDrop(laneId: string) {
  if (!project.value || !draggingTaskId.value) return
  const task = project.value.tasks.find(item => item.taskId === draggingTaskId.value)
  if (!task || !canMoveTask(task)) return
  await projectStore.moveTask(project.value.projectId, draggingTaskId.value, laneId)
  ElMessage.success('任务状态已更新')
  handleTaskDragEnd()
}

async function promptAddLane() {
  if (!project.value || !canAddLane.value) return
  try {
    const { value } = await ElMessageBox.prompt('请输入泳道名称', '新增泳道', {
      inputPlaceholder: '例如：待客户反馈'
    })
    if (!value?.trim()) return
    await projectStore.addLane(project.value.projectId, value)
    ElMessage.success('泳道已新增')
  } catch {
    // noop
  }
}

async function promptRenameLane(laneId: string) {
  if (!project.value || !canEditLane.value) return
  const lane = project.value.lanes.find(item => item.laneId === laneId)
  if (!lane) return
  try {
    const { value } = await ElMessageBox.prompt('修改泳道名称', '编辑泳道', {
      inputValue: lane.name
    })
    if (!value?.trim()) return
    await projectStore.updateLane(project.value.projectId, laneId, value)
    ElMessage.success('泳道已更新')
  } catch {
    // noop
  }
}

async function handleDeleteLane(laneId: string) {
  if (!project.value || !canDeleteLane.value) return
  const lane = project.value.lanes.find(item => item.laneId === laneId)
  if (!lane || lane.system) return
  try {
    await ElMessageBox.confirm(`删除泳道“${lane.name}”后，泳道内任务会自动回到“未开始”。是否继续？`, '提示', {
      type: 'warning'
    })
    await projectStore.deleteLane(project.value.projectId, laneId)
    ElMessage.success('泳道已删除')
  } catch {
    // noop
  }
}

async function handleSendAiMessage() {
  if (!project.value || !aiInput.value.trim() || !canUseAiChat.value) return
  await projectStore.handleAiCommand(project.value.projectId, aiInput.value.trim())
  aiInput.value = ''
}

async function handleSendTaskAiMessage() {
  if (!project.value || !currentTaskConversation.value || !taskAiInput.value.trim() || !canUseCurrentTaskAi.value) return
  await projectStore.handleTaskAiCommand(project.value.projectId, currentTaskConversation.value.taskId, taskAiInput.value.trim())
  taskAiInput.value = ''
}

function openAddMember() {
  if (!canAddMember.value) return
  editingMember.value = null
  showMemberDialog.value = true
}

function openEditMember(member: ProjectMember) {
  if (!canModifyMemberPermission.value) return
  editingMember.value = member
  showMemberDialog.value = true
}

async function handleSubmitMember(payload: {
  userId: string
  memberName: string
  account: string
  deptName?: string
  role: ProjectMember['role']
  status: ProjectMember['status']
  permissions: ProjectPermission[]
  remark?: string
}) {
  if (!project.value) return

  if (editingMember.value) {
    const original = editingMember.value
    if (payload.role !== original.role) {
      await projectStore.updateMemberRole(project.value.projectId, original.userId, payload.role)
    }
    if (JSON.stringify([...payload.permissions].sort()) !== JSON.stringify([...original.permissions].sort())) {
      await projectStore.updateMemberPermissions(project.value.projectId, original.userId, payload.permissions)
    }
    if (payload.status !== original.status) {
      await projectStore.updateMemberStatus(project.value.projectId, original.userId, payload.status)
    }

    const metaChanged = payload.memberName !== original.memberName
      || payload.account !== original.account
      || (payload.deptName || '') !== (original.deptName || '')
      || (payload.remark || '') !== (original.remark || '')

    if (metaChanged) {
      await projectStore.addMember(project.value.projectId, payload)
    }
    ElMessage.success('成员信息已更新')
    return
  }

  await projectStore.addMember(project.value.projectId, payload)
  ElMessage.success('成员添加成功')
}

async function removeMember(member: ProjectMember) {
  if (!project.value || !canRemoveMember.value) return
  try {
    await ElMessageBox.confirm(`确定移除成员“${member.memberName}”吗？`, '提示', { type: 'warning' })
    await projectStore.updateMemberStatus(project.value.projectId, member.userId, 'REMOVED')
    ElMessage.success('成员已移除')
  } catch {
    // noop
  }
}

async function toggleMemberStatus(member: ProjectMember) {
  if (!project.value || !canModifyMemberPermission.value) return
  const nextStatus = member.status === 'DISABLED' ? 'ACTIVE' : 'DISABLED'
  await projectStore.updateMemberStatus(project.value.projectId, member.userId, nextStatus)
  ElMessage.success(nextStatus === 'ACTIVE' ? '成员已启用' : '成员已停用')
}

function memberActionLabel(action: string) {
  switch (action) {
    case 'ADD_MEMBER':
      return '添加成员'
    case 'REMOVE_MEMBER':
      return '移除成员'
    case 'UPDATE_ROLE':
      return '修改角色'
    case 'UPDATE_PERMISSION':
      return '修改权限'
    case 'UPDATE_STATUS':
      return '更新状态'
    default:
      return action
  }
}
</script>
