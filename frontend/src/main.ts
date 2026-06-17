import { createApp } from 'vue'
import { createPinia } from 'pinia'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import '@fontsource/inter/300.css'
import '@fontsource/inter/400.css'
import '@fontsource/inter/500.css'
import '@fontsource/inter/600.css'
import '@fontsource/inter/700.css'
import '@fontsource/inter/800.css'
import '@fontsource/inter/900.css'
import '@fontsource-variable/noto-sans-sc'
import 'material-symbols/outlined.css'
import { isLegacyAndroidWebView, isNativeMobileRuntime } from '@/utils/nativeMobileRuntime'
import { ensureNativePrivacyConsent } from '@/utils/nativePrivacyConsent'
import './styles/iconfont.css'
import './styles/main.css'
import './styles/wk-crm-el-field-skin.css'

async function scheduleNativeMobileRuntime(nativeMobileRuntime: boolean): Promise<void> {
  if (!nativeMobileRuntime) return

  try {
    const {
      scheduleCapacitorUpdateCheck,
      scheduleLiveUpdateHealthReport
    } = await import('@/utils/capacitorUpdate')

    scheduleLiveUpdateHealthReport()
    scheduleCapacitorUpdateCheck()
  } catch (error) {
    console.warn('Failed to load Capacitor update module:', error)
  }

  try {
    const { configureNativeKeyboard } = await import('@/utils/capacitorKeyboard')
    configureNativeKeyboard()
  } catch (error) {
    console.warn('Failed to load Capacitor keyboard module:', error)
  }
}

async function bootstrap(): Promise<void> {
  const nativeMobileRuntime = isNativeMobileRuntime()
  const canContinue = await ensureNativePrivacyConsent({ nativeRuntime: nativeMobileRuntime })
  if (!canContinue) return

  const [
    { default: ElementPlus },
    { default: zhCn },
    { default: WkIcon },
    { useTheme },
    { default: App },
    { default: router }
  ] = await Promise.all([
    import('element-plus'),
    import('element-plus/es/locale/lang/zh-cn'),
    import('@/components/common/WkIcon.vue'),
    import('@/composables/useTheme'),
    import('./App.vue'),
    import('./router')
  ])

  useTheme()

  if (nativeMobileRuntime) {
    document.documentElement.classList.add('wk-native-mobile')
    if (isLegacyAndroidWebView()) {
      document.documentElement.classList.add('wk-android-legacy-webview')
    }
    void import('@/utils/capacitorSafeArea').catch((error) => {
      console.warn('Failed to load native safe area plugin:', error)
    })
  }

  const app = createApp(App)

  app.use(createPinia())
  app.use(router)
  app.use(ElementPlus, { locale: zhCn })
  app.component('WkIcon', WkIcon)

  await router.isReady()
  app.mount('#app')
  void scheduleNativeMobileRuntime(nativeMobileRuntime)
}

void bootstrap().catch((error) => {
  console.error('Failed to bootstrap app:', error)
})
