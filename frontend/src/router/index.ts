import { createRouter, createWebHashHistory, type RouteRecordRaw } from 'vue-router'
import { getOidcSessionToken } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import { getToken } from '@/utils/request'

const settingsComponent = () => import('@/views/settings/SettingsView.vue')
const settingsMeta = {
  title: '系统设置',
  icon: 'set',
  permission: ['user', 'role', 'config', 'dept', 'customField']
}

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    children: [
      {
        path: '',
        redirect: '/chat'
      },
      {
        path: 'chat',
        name: 'Chat',
        component: () => import('@/views/chat/ChatView.vue'),
        meta: { title: 'AI 助手', icon: 'ai', permission: 'chat' }
      },
      {
        path: 'customer',
        name: 'CustomerList',
        component: () => import('@/views/customer/CustomerListView.vue'),
        meta: { title: '客户管理', icon: 'group', permission: 'customer:view' }
      },
      {
        path: 'customer/:id',
        name: 'CustomerDetail',
        component: () => import('@/views/customer/CustomerDetailView.vue'),
        meta: { title: '客户详情', hidden: true, permission: 'customer:view' }
      },
      {
        path: 'address-book',
        name: 'AddressBook',
        component: () => import('@/views/addressBook/AddressBookListView.vue'),
        meta: { title: '通讯录', icon: 'contacts', permission: 'addressBook:list' }
      },
      {
        path: 'relation',
        name: 'RelationList',
        component: () => import('@/views/relation/RelationListView.vue'),
        meta: { title: '关系', icon: 'contacts', permission: 'relation:view' }
      },
      {
        path: 'task',
        name: 'TaskList',
        component: () => import('@/views/task/TaskListView.vue'),
        meta: { title: '任务管理', icon: 'task_alt', permission: 'task:view' }
      },
      {
        path: 'project',
        name: 'ProjectList',
        component: () => import('@/views/project/ProjectListView.vue'),
        meta: { title: '项目管理', icon: 'task_alt', permission: 'task' }
      },
      {
        path: 'project/:id',
        name: 'ProjectDetail',
        component: () => import('@/views/project/ProjectDetailView.vue'),
        meta: { title: '项目详情', hidden: true, permission: 'task' }
      },
      {
        path: 'calendar',
        name: 'Calendar',
        component: () => import('@/views/calendar/CalendarView.vue'),
        meta: { title: '日程安排', icon: 'calendar_today', permission: 'schedule' }
      },
      {
        path: 'mail',
        name: 'Mail',
        component: () => import('@/views/mail/MailView.vue'),
        meta: { title: '邮箱', icon: 'mail', permission: 'mail:view' }
      },
      {
        path: 'scrm',
        name: 'WecomScrm',
        component: () => import('@/views/wecom/WecomScrmView.vue'),
        meta: { title: '企业微信', icon: 'forum', permission: 'wecomCustomerSession:view' }
      },
      {
        path: 'tencent-meetings',
        name: 'TencentMeetings',
        component: () => import('@/views/tencentMeeting/TencentMeetingView.vue'),
        meta: { title: '腾讯会议', icon: 'video_camera_front', permission: 'tencentMeeting:view' }
      },
      {
        path: 'knowledge',
        name: 'Knowledge',
        component: () => import('@/views/knowledge/KnowledgeView.vue'),
        meta: { title: '知识库', icon: 'menu_book', permission: 'knowledge' }
      },
      {
        path: 'sync',
        name: 'DataSync',
        component: () => import('@/views/sync/SyncDataView.vue'),
        meta: { title: '数据同步', icon: 'sync_alt', permission: 'config' }
      },
      {
        path: 'settings',
        redirect: '/settings/team'
      },
      {
        path: 'settings/team',
        name: 'SettingsTeam',
        component: settingsComponent,
        meta: settingsMeta
      },
      {
        path: 'settings/role',
        name: 'SettingsRole',
        component: settingsComponent,
        meta: settingsMeta
      },
      {
        path: 'settings/system',
        redirect: '/settings/system/enterprise'
      },
      {
        path: 'settings/system/profile',
        redirect: '/settings/system/enterprise'
      },
      {
        path: 'settings/system/:systemTab(enterprise|api|agent|storage|customField)',
        name: 'SettingsSystem',
        component: settingsComponent,
        meta: settingsMeta
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/'
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach(async (to, _from, next) => {
  const token = getToken()
  const requiresAuth = to.meta.requiresAuth !== false

  if (requiresAuth && !token) {
    next({ name: 'Login', query: { redirect: to.fullPath } })
  } else if ((to.name === 'Login' || to.name === 'Register') && token) {
    let redirect = to.query.redirect as string
    if (redirect) {
      if (redirect.includes('/oauth2/authorize')) {
        try {
          const { sessionToken } = await getOidcSessionToken()
          const url = new URL(redirect)
          url.searchParams.set('session_token', sessionToken)
          redirect = url.toString()
        } catch (error) {
          console.error('Failed to get OIDC session token:', error)
        }
      }

      if (redirect.startsWith('http://') || redirect.startsWith('https://')) {
        window.location.href = redirect
        return
      }

      next(redirect)
    } else {
      next({ name: 'Chat' })
    }
  } else if (requiresAuth && token) {
    const userStore = useUserStore()
    if (!userStore.userInfo || !userStore.permissionsLoaded) {
      try {
        await userStore.fetchUserInfo()
      } catch (error) {
        console.error('Failed to fetch user info:', error)
        next({ name: 'Login', query: { redirect: to.fullPath } })
        return
      }
    }

    const permission = to.meta.permission as string | string[] | undefined
    if (permission) {
      const hasAccess = Array.isArray(permission)
        ? permission.some(p => userStore.hasPermission(p))
        : (permission === 'chat' || permission === 'addressBook:list' || userStore.hasPermission(permission))
      if (!hasAccess) {
        next({ name: 'Chat' })
        return
      }
    }

    next()
  } else {
    next()
  }
})

export default router
