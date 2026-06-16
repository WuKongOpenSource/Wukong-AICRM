<template>
  <div class="space-y-6">
    <el-card shadow="never" class="!border-slate-200">
      <template #header>
        <div class="flex items-center justify-between">
          <span class="font-medium">{{ copy.title }}</span>
          <el-button :loading="loading" @click="loadData">{{ copy.refresh }}</el-button>
        </div>
      </template>

      <div v-if="loading" class="py-10 text-center text-slate-400">
        <el-icon class="is-loading"><Loading /></el-icon>
      </div>

      <div v-else class="space-y-3">
        <div
          v-for="provider in providers"
          :key="provider.provider"
          class="flex flex-col gap-4 rounded-lg border border-slate-200 bg-white p-4 md:flex-row md:items-center md:justify-between"
        >
          <div class="flex min-w-0 items-center gap-3">
            <span class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-slate-100 text-sm font-bold text-primary">
              {{ providerMark(provider.provider) }}
            </span>
            <div class="min-w-0">
              <div class="flex flex-wrap items-center gap-2">
                <span class="font-medium text-slate-900">{{ provider.name }}</span>
                <el-tag v-if="provider.enabled" type="success" size="small" effect="plain">{{ copy.configured }}</el-tag>
                <el-tag v-else type="info" size="small" effect="plain">{{ copy.notConfigured }}</el-tag>
                <el-tag v-if="bindingFor(provider.provider)" type="primary" size="small" effect="plain">{{ copy.bound }}</el-tag>
              </div>
              <div class="mt-1 truncate text-sm text-slate-500">
                <template v-if="bindingFor(provider.provider)">
                  {{ bindingFor(provider.provider)?.displayName || bindingFor(provider.provider)?.email || bindingFor(provider.provider)?.subject }}
                </template>
                <template v-else>
                  {{ copy.noBinding }}
                </template>
              </div>
            </div>
          </div>

          <div class="flex shrink-0 gap-2">
            <el-button
              v-if="!bindingFor(provider.provider)"
              type="primary"
              plain
              :disabled="!provider.enabled"
              :loading="bindingProvider === provider.provider"
              @click="startBind(provider.provider)"
            >
              {{ copy.bind }}
            </el-button>
            <el-button
              v-else
              type="danger"
              plain
              :loading="unbindingProvider === provider.provider"
              @click="unbind(provider.provider)"
            >
              {{ copy.unbind }}
            </el-button>
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import {
  getExternalAuthBindings,
  getExternalAuthProviders,
  getExternalBindAuthorizeUrl,
  unbindExternalAuth
} from '@/api/auth'
import type { ExternalAuthBinding, ExternalAuthProvider, ExternalAuthProviderCode } from '@/types/api'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const providers = ref<ExternalAuthProvider[]>([])
const bindings = ref<ExternalAuthBinding[]>([])
const bindingProvider = ref<ExternalAuthProviderCode | ''>('')
const unbindingProvider = ref<ExternalAuthProviderCode | ''>('')

const copy = {
  title: '\u5916\u90e8\u767b\u5f55',
  refresh: '\u5237\u65b0',
  configured: '\u5df2\u914d\u7f6e',
  notConfigured: '\u672a\u914d\u7f6e',
  bound: '\u5df2\u7ed1\u5b9a',
  noBinding: '\u672a\u7ed1\u5b9a\u8d26\u53f7',
  bind: '\u7ed1\u5b9a',
  unbind: '\u89e3\u7ed1',
  confirmTitle: '\u786e\u8ba4',
  confirmBody: '\u786e\u5b9a\u8981\u89e3\u7ed1\u8be5\u5916\u90e8\u8d26\u53f7\u5417?',
  boundSuccess: '\u5916\u90e8\u8d26\u53f7\u5df2\u7ed1\u5b9a',
  unboundSuccess: '\u5916\u90e8\u8d26\u53f7\u5df2\u89e3\u7ed1',
  bindFailed: '\u5916\u90e8\u8d26\u53f7\u7ed1\u5b9a\u5931\u8d25'
}

const bindingMap = computed(() =>
  new Map(bindings.value.filter(binding => binding.bound).map((binding) => [binding.provider, binding]))
)

onMounted(async () => {
  await handleExternalBindQuery()
  await loadData()
})

async function loadData() {
  loading.value = true
  try {
    const [providerList, bindingList] = await Promise.all([
      getExternalAuthProviders(),
      getExternalAuthBindings()
    ])
    providers.value = providerList
    bindings.value = bindingList
  } catch (error) {
    console.error('Load external login settings failed:', error)
  } finally {
    loading.value = false
  }
}

function bindingFor(provider: ExternalAuthProviderCode): ExternalAuthBinding | undefined {
  return bindingMap.value.get(provider)
}

function providerMark(provider: ExternalAuthProviderCode): string {
  if (provider === 'google') return 'G'
  if (provider === 'outlook') return 'M'
  if (provider === 'wechat') return 'W'
  return '?'
}

function buildBindRedirect(): string {
  const resolved = router.resolve({ path: '/settings/system/auth' })
  return `${window.location.origin}${window.location.pathname}${window.location.search}${resolved.href}`
}

async function startBind(provider: ExternalAuthProviderCode) {
  bindingProvider.value = provider
  try {
    const { authorizeUrl } = await getExternalBindAuthorizeUrl(provider, buildBindRedirect())
    window.location.href = authorizeUrl
  } catch (error) {
    console.error('Start external bind failed:', error)
  } finally {
    bindingProvider.value = ''
  }
}

async function unbind(provider: ExternalAuthProviderCode) {
  try {
    await ElMessageBox.confirm(copy.confirmBody, copy.confirmTitle, {
      confirmButtonText: copy.unbind,
      cancelButtonText: '\u53d6\u6d88',
      type: 'warning'
    })
  } catch {
    return
  }

  unbindingProvider.value = provider
  try {
    await unbindExternalAuth(provider)
    ElMessage.success(copy.unboundSuccess)
    await loadData()
  } catch (error) {
    console.error('Unbind external account failed:', error)
  } finally {
    unbindingProvider.value = ''
  }
}

async function handleExternalBindQuery() {
  const bindResult = typeof route.query.externalBind === 'string' ? route.query.externalBind : ''
  const externalError = typeof route.query.externalAuthError === 'string' ? route.query.externalAuthError : ''
  if (bindResult === 'success') {
    ElMessage.success(copy.boundSuccess)
    await clearExternalBindQuery()
    return
  }
  if (externalError) {
    ElMessage.error(copy.bindFailed)
    await clearExternalBindQuery()
  }
}

async function clearExternalBindQuery() {
  const query = { ...route.query }
  delete query.externalBind
  delete query.externalAuthError
  delete query.provider
  await router.replace({ path: route.path, query })
}
</script>
