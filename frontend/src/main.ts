import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import '@fontsource/inter/300.css'
import '@fontsource/inter/400.css'
import '@fontsource/inter/500.css'
import '@fontsource/inter/600.css'
import '@fontsource/inter/700.css'
import '@fontsource/inter/800.css'
import '@fontsource/inter/900.css'
import '@fontsource-variable/noto-sans-sc'
import 'material-symbols/outlined.css'
import WkIcon from '@/components/common/WkIcon.vue'
import { useTheme } from '@/composables/useTheme'
import { isNativeMobileRuntime } from '@/utils/nativeMobileRuntime'

import App from './App.vue'
import router from './router'
import './styles/iconfont.css'
import './styles/main.css'
import './styles/wk-crm-el-field-skin.css'

useTheme()

const nativeMobileRuntime = isNativeMobileRuntime()

if (nativeMobileRuntime) {
  document.documentElement.classList.add('wk-native-mobile')
  void import('@/utils/capacitorSafeArea').catch((error) => {
    console.warn('Failed to load native safe area plugin:', error)
  })
}

async function scheduleNativeMobileRuntime(): Promise<void> {
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

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })
app.component('WkIcon', WkIcon)

router.isReady().then(() => {
  app.mount('#app')
  void scheduleNativeMobileRuntime()
})
