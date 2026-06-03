import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { useUserStore } from '@/stores/user'
import type {
  ProjectChatMessage,
  ProjectCreatePayload,
  ProjectEntity,
  ProjectMemberPayload,
  ProjectMemberStatus,
  ProjectPermission,
  ProjectRole,
  ProjectTask,
  ProjectTaskChatMessage,
  ProjectTaskPayload,
  ProjectTaskUpdatePayload,
  ProjectUpdatePayload
} from '@/types/project'
import {
  addLane as apiAddLane,
  addProjectMember,
  archiveProject as apiArchiveProject,
  createProject as apiCreateProject,
  createProjectTask,
  deleteLane as apiDeleteLane,
  deleteProject as apiDeleteProject,
  deleteProjectTask,
  getProjectDetail,
  moveProjectTask,
  queryProjectList,
  sendProjectAiCommand,
  sendTaskAiCommand,
  updateLane as apiUpdateLane,
  updateProject as apiUpdateProject,
  updateProjectMemberPermissions,
  updateProjectMemberRole,
  updateProjectMemberStatus,
  updateProjectTask
} from '@/api/project'
import {
  canEditTask,
  canMoveTask,
  canViewTask,
  countIncompleteTasks,
  findProjectMember,
  hasProjectPermission
} from '@/utils/project'

function nowIso() {
  return new Date().toISOString()
}

export const useProjectStore = defineStore('project', () => {
  const userStore = useUserStore()
  const initialized = ref(false)
  const loading = ref(false)
  const projects = ref<ProjectEntity[]>([])

  const currentUserId = computed(() => String(userStore.userId || ''))
  const currentUserName = computed(() => userStore.realname || userStore.username || '当前用户')
  const isSystemAdmin = computed(() =>
    Boolean(
      projects.value.some(project => project.systemAdmin)
      || userStore.userInfo?.tenantCreator
      || userStore.hasPermission('config')
      || userStore.hasPermission('user')
      || userStore.hasPermission('role')
    )
  )

  const projectSummaries = computed(() =>
    projects.value
      .map(project => ({
        ...project,
        taskCount: project.taskCount ?? project.tasks.length,
        incompleteTaskCount: project.incompleteTaskCount ?? countIncompleteTasks(project)
      }))
      .sort((a, b) => new Date(b.updateTime).getTime() - new Date(a.updateTime).getTime())
  )

  const accessibleProjectSummaries = computed(() => projectSummaries.value)

  async function ensureInitialized(force = false) {
    if (initialized.value && !force) return
    loading.value = true
    try {
      projects.value = await queryProjectList()
      initialized.value = true
    } finally {
      loading.value = false
    }
  }

  async function loadProject(projectId: string) {
    const project = await getProjectDetail(projectId)
    upsertProject(project)
    initialized.value = true
    return project
  }

  async function createProject(payload: ProjectCreatePayload) {
    const project = await apiCreateProject(payload)
    upsertProject(project)
    return project
  }

  async function updateProject(payload: ProjectUpdatePayload) {
    const project = await apiUpdateProject(payload)
    upsertProject(project)
    return project
  }

  async function deleteProject(projectId: string) {
    await apiDeleteProject(projectId)
    projects.value = projects.value.filter(project => project.projectId !== projectId)
  }

  async function archiveProject(projectId: string) {
    const project = await apiArchiveProject(projectId)
    upsertProject(project)
    return project
  }

  function getProjectById(projectId: string) {
    return projects.value.find(project => project.projectId === projectId) || null
  }

  function getTaskById(projectId: string, taskId: string) {
    const project = getProjectById(projectId)
    return project?.tasks.find(task => task.taskId === taskId) || null
  }

  async function createTask(projectId: string, payload: ProjectTaskPayload) {
    const project = await createProjectTask(projectId, payload)
    upsertProject(project)
    return project.tasks.find(task => task.title === payload.title) || null
  }

  async function updateTask(projectId: string, payload: ProjectTaskUpdatePayload) {
    const project = await updateProjectTask(projectId, payload)
    upsertProject(project)
    return project.tasks.find(task => task.taskId === payload.taskId) || null
  }

  async function deleteTask(projectId: string, taskId: string) {
    const project = await deleteProjectTask(projectId, taskId)
    upsertProject(project)
  }

  async function moveTask(projectId: string, taskId: string, laneId: string) {
    const project = await moveProjectTask(projectId, taskId, laneId)
    upsertProject(project)
    return project.tasks.find(task => task.taskId === taskId) || null
  }

  async function addLane(projectId: string, laneName: string) {
    const project = await apiAddLane(projectId, laneName)
    upsertProject(project)
    return project.lanes.find(lane => lane.name === laneName.trim()) || null
  }

  async function updateLane(projectId: string, laneId: string, laneName: string) {
    const project = await apiUpdateLane(projectId, laneId, laneName)
    upsertProject(project)
    return project.lanes.find(lane => lane.laneId === laneId) || null
  }

  async function deleteLane(projectId: string, laneId: string) {
    const project = await apiDeleteLane(projectId, laneId)
    upsertProject(project)
    return true
  }

  async function addMember(projectId: string, payload: ProjectMemberPayload) {
    const project = await addProjectMember(projectId, payload)
    upsertProject(project)
    return project.members.find(member => member.userId === payload.userId) || null
  }

  async function updateMemberRole(projectId: string, userId: string, role: ProjectRole) {
    const project = await updateProjectMemberRole(projectId, userId, role)
    upsertProject(project)
    return project.members.find(member => member.userId === userId) || null
  }

  async function updateMemberPermissions(projectId: string, userId: string, permissions: ProjectPermission[]) {
    const project = await updateProjectMemberPermissions(projectId, userId, permissions)
    upsertProject(project)
    return project.members.find(member => member.userId === userId) || null
  }

  async function updateMemberStatus(projectId: string, userId: string, status: ProjectMemberStatus) {
    const project = await updateProjectMemberStatus(projectId, userId, status)
    upsertProject(project)
    return project.members.find(member => member.userId === userId) || null
  }

  async function handleAiCommand(projectId: string, content: string) {
    const project = await sendProjectAiCommand(projectId, content)
    upsertProject(project)
    return project.chatMessages.at(-1)?.content || ''
  }

  async function handleTaskAiCommand(projectId: string, taskId: string, content: string) {
    const project = await sendTaskAiCommand(projectId, taskId, content)
    upsertProject(project)
    return project.tasks.find(task => task.taskId === taskId)?.chatMessages.at(-1)?.content || ''
  }

  function getUserProjectPermission(projectId: string, permission: ProjectPermission) {
    const project = getProjectById(projectId)
    if (!project) return false
    if (project.systemAdmin || project.currentUserPermissions?.includes(permission)) return true
    return hasProjectPermission(project, currentUserId.value, permission, isSystemAdmin.value)
  }

  function canCurrentUserViewTask(projectId: string, task: ProjectTask) {
    const project = getProjectById(projectId)
    if (!project) return false
    if (project.systemAdmin) return true
    if (project.currentUserPermissions?.includes('VIEW_PROJECT')) return true
    return canViewTask(project, task, currentUserId.value, isSystemAdmin.value)
  }

  function canCurrentUserEditTask(projectId: string, task: ProjectTask) {
    const project = getProjectById(projectId)
    if (!project) return false
    if (project.systemAdmin) return true
    if (project.currentUserPermissions?.includes('EDIT_TASK')) {
      if (project.currentUserRole === 'OWNER' || project.currentUserRole === 'ADMIN') return true
      return task.ownerId === currentUserId.value || task.participantIds?.includes(currentUserId.value)
    }
    return canEditTask(project, task, currentUserId.value, isSystemAdmin.value)
  }

  function canCurrentUserMoveTask(projectId: string, task: ProjectTask) {
    const project = getProjectById(projectId)
    if (!project) return false
    if (project.systemAdmin) return true
    if (project.currentUserPermissions?.includes('MOVE_TASK')) {
      if (project.currentUserRole === 'OWNER' || project.currentUserRole === 'ADMIN') return true
      return task.ownerId === currentUserId.value
    }
    return canMoveTask(project, task, currentUserId.value, isSystemAdmin.value)
  }

  function canCurrentUserUseTaskAi(projectId: string, task: ProjectTask) {
    return getUserProjectPermission(projectId, 'USE_AI_CHAT') && canCurrentUserViewTask(projectId, task)
  }

  function canCurrentUserUploadTaskAttachment(projectId: string, task: ProjectTask) {
    return getUserProjectPermission(projectId, 'UPLOAD_ATTACHMENT') && canCurrentUserViewTask(projectId, task)
  }

  function canCurrentUserCreateTaskSchedule(projectId: string, task: ProjectTask) {
    return getUserProjectPermission(projectId, 'CREATE_SCHEDULE') && canCurrentUserViewTask(projectId, task)
  }

  function canCurrentUserAddTaskNote(projectId: string, task: ProjectTask) {
    return getUserProjectPermission(projectId, 'VIEW_PROJECT') && canCurrentUserViewTask(projectId, task)
  }

  function appendChatMessage(projectId: string, message: Omit<ProjectChatMessage, 'messageId' | 'createTime'>) {
    const project = getProjectById(projectId)
    if (!project) return null
    const record: ProjectChatMessage = {
      messageId: `local-${Date.now()}`,
      createTime: nowIso(),
      ...message
    }
    project.chatMessages.push(record)
    return record
  }

  function appendTaskChatMessage(projectId: string, taskId: string, message: Omit<ProjectTaskChatMessage, 'messageId' | 'createTime'>) {
    const task = getTaskById(projectId, taskId)
    if (!task) return null
    const record: ProjectTaskChatMessage = {
      messageId: `local-${Date.now()}`,
      createTime: nowIso(),
      ...message
    }
    task.chatMessages.push(record)
    return record
  }

  function upsertProject(project: ProjectEntity) {
    const index = projects.value.findIndex(item => item.projectId === project.projectId)
    if (index >= 0) {
      projects.value[index] = project
    } else {
      projects.value.unshift(project)
    }
  }

  return {
    projects,
    loading,
    projectSummaries,
    accessibleProjectSummaries,
    currentUserId,
    currentUserName,
    isSystemAdmin,
    ensureInitialized,
    loadProject,
    createProject,
    updateProject,
    deleteProject,
    archiveProject,
    getProjectById,
    getTaskById,
    createTask,
    updateTask,
    deleteTask,
    moveTask,
    addTaskAttachment: appendTaskChatMessage,
    addTaskSchedule: appendTaskChatMessage,
    addTaskNote: appendTaskChatMessage,
    addLane,
    updateLane,
    deleteLane,
    addMember,
    updateMemberRole,
    updateMemberPermissions,
    updateMemberStatus,
    appendChatMessage,
    appendTaskChatMessage,
    handleAiCommand,
    handleTaskAiCommand,
    getUserProjectPermission,
    canCurrentUserViewTask,
    canCurrentUserEditTask,
    canCurrentUserMoveTask,
    canCurrentUserUseTaskAi,
    canCurrentUserUploadTaskAttachment,
    canCurrentUserCreateTaskSchedule,
    canCurrentUserAddTaskNote,
    findProjectMember
  }
})
