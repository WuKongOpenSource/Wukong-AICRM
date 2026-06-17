import { App } from '@capacitor/app'
import { isNativeMobileRuntime } from './nativeMobileRuntime'
import {
  hasStoredNativePrivacyConsent,
  resolveNativePrivacyBootstrapState,
  storeNativePrivacyConsent,
  type NativePrivacyBootstrapState,
  type PrivacyConsentStorage
} from './nativePrivacyConsentCore'

const USER_AGREEMENT_URL = 'https://file.72crm.com/static/law/72crm_ai_service.txt'
const PRIVACY_POLICY_URL = 'https://file.72crm.com/static/law/72crm_ai_privacy.txt'

export type NativePrivacyConsentOptions = {
  nativeRuntime?: boolean
  storage?: PrivacyConsentStorage | null
  exitApp?: () => Promise<boolean>
}

function getBrowserStorage(): PrivacyConsentStorage | null {
  if (typeof window === 'undefined') return null

  try {
    return window.localStorage
  } catch {
    return null
  }
}

export function getNativePrivacyBootstrapState(
  nativeRuntime = isNativeMobileRuntime(),
  storage = getBrowserStorage()
): NativePrivacyBootstrapState {
  return resolveNativePrivacyBootstrapState({
    nativeRuntime,
    hasConsent: hasStoredNativePrivacyConsent(storage)
  })
}

export function markNativePrivacyConsentAccepted(
  storage = getBrowserStorage()
): boolean {
  return storeNativePrivacyConsent(storage)
}

export async function exitNativeApp(): Promise<boolean> {
  try {
    await App.exitApp()
    return true
  } catch (error) {
    console.warn('Failed to exit native app:', error)
    return false
  }
}

function openConsentLegalDocument(url: string): void {
  if (typeof window === 'undefined') return

  window.open(url, '_blank', 'noopener,noreferrer')
}

function renderNativePrivacyConsentDialog(
  storage: PrivacyConsentStorage | null,
  exitApp: () => Promise<boolean>
): Promise<boolean> {
  return new Promise((resolve) => {
    const host = document.getElementById('app') || document.body
    const root = document.createElement('div')
    let isSettled = false

    root.className = 'wk-native-privacy-consent'
    root.innerHTML = `
      <style>
        .wk-native-privacy-consent {
          position: fixed;
          top: 0;
          right: 0;
          bottom: 0;
          left: 0;
          z-index: 2147483647;
          display: flex;
          align-items: center;
          justify-content: center;
          box-sizing: border-box;
          padding: 20px;
          background: rgba(15, 23, 42, 0.58);
          color: #0f172a;
          font-family: Inter, "Noto Sans SC", -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
        }

        .wk-native-privacy-consent__dialog {
          width: 100%;
          max-width: 420px;
          max-height: 86vh;
          overflow-y: auto;
          box-sizing: border-box;
          border-radius: 8px;
          background: #fff;
          padding: 24px;
          box-shadow: 0 24px 60px rgba(15, 23, 42, 0.26);
          -webkit-overflow-scrolling: touch;
        }

        .wk-native-privacy-consent__title {
          margin: 0;
          color: #0f172a;
          font-size: 20px;
          font-weight: 800;
          line-height: 1.35;
        }

        .wk-native-privacy-consent__content {
          margin: 14px 0 0;
          color: #334155;
          font-size: 15px;
          line-height: 1.75;
        }

        .wk-native-privacy-consent__content p {
          margin: 0 0 10px;
        }

        .wk-native-privacy-consent__content p:last-child {
          margin-bottom: 0;
        }

        .wk-native-privacy-consent__link {
          color: #137fec;
          font-weight: 700;
          text-decoration: none;
        }

        .wk-native-privacy-consent__link:focus,
        .wk-native-privacy-consent__link:hover {
          text-decoration: underline;
        }

        .wk-native-privacy-consent__actions {
          display: grid;
          grid-template-columns: 1fr 1fr;
          gap: 12px;
          margin-top: 22px;
        }

        .wk-native-privacy-consent__button {
          min-height: 44px;
          border: 1px solid #cbd5e1;
          border-radius: 8px;
          background: #fff;
          color: #334155;
          cursor: pointer;
          font: inherit;
          font-size: 15px;
          font-weight: 800;
        }

        .wk-native-privacy-consent__button:disabled {
          cursor: wait;
          opacity: 0.72;
        }

        .wk-native-privacy-consent__button--primary {
          border-color: #137fec;
          background: #137fec;
          color: #fff;
        }

        .wk-native-privacy-consent__blocked {
          display: none;
          margin-top: 16px;
          border-radius: 8px;
          background: #f8fafc;
          padding: 12px;
          color: #475569;
          font-size: 14px;
          line-height: 1.7;
        }

        .wk-native-privacy-consent.is-blocked .wk-native-privacy-consent__blocked {
          display: block;
        }

        @media (max-width: 360px) {
          .wk-native-privacy-consent {
            padding: 14px;
          }

          .wk-native-privacy-consent__dialog {
            padding: 20px;
          }

          .wk-native-privacy-consent__actions {
            grid-template-columns: 1fr;
          }
        }
      </style>
      <section
        class="wk-native-privacy-consent__dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="wk-native-privacy-consent-title"
        tabindex="-1"
      >
        <h1 id="wk-native-privacy-consent-title" class="wk-native-privacy-consent__title">
          请阅读并同意相关协议
        </h1>
        <div class="wk-native-privacy-consent__content">
          <p>
            欢迎使用悟空AI CRM。为了保障您的权益，请您在继续使用前仔细阅读
            <a class="wk-native-privacy-consent__link" href="${PRIVACY_POLICY_URL}" data-legal-document-url="${PRIVACY_POLICY_URL}" target="_blank" rel="noopener noreferrer">《隐私政策》</a>
            和
            <a class="wk-native-privacy-consent__link" href="${USER_AGREEMENT_URL}" data-legal-document-url="${USER_AGREEMENT_URL}" target="_blank" rel="noopener noreferrer">《用户协议》</a>。
          </p>
          <p>如果您点击“不同意”，应用将退出。</p>
        </div>
        <div class="wk-native-privacy-consent__blocked" role="status">
          您已选择不同意，应用已停止启动。若系统未自动关闭，请直接关闭本应用。
        </div>
        <div class="wk-native-privacy-consent__actions">
          <button type="button" class="wk-native-privacy-consent__button" data-action="reject">不同意</button>
          <button type="button" class="wk-native-privacy-consent__button wk-native-privacy-consent__button--primary" data-action="accept">同意</button>
        </div>
      </section>
    `

    while (host.firstChild) {
      host.removeChild(host.firstChild)
    }
    host.appendChild(root)

    const dialog = root.querySelector<HTMLElement>('.wk-native-privacy-consent__dialog')
    const acceptButton = root.querySelector<HTMLButtonElement>('[data-action="accept"]')
    const rejectButton = root.querySelector<HTMLButtonElement>('[data-action="reject"]')
    const legalLinks = Array.from(root.querySelectorAll<HTMLAnchorElement>('[data-legal-document-url]'))

    function handleKeydown(event: KeyboardEvent): void {
      if (event.key === 'Escape') {
        event.preventDefault()
        event.stopPropagation()
      }
    }

    function handleLegalLinkClick(event: MouseEvent): void {
      event.preventDefault()
      event.stopPropagation()

      const target = event.currentTarget
      if (!(target instanceof HTMLAnchorElement)) return

      const url = target.dataset.legalDocumentUrl || target.href
      openConsentLegalDocument(url)
    }

    function cleanup(): void {
      document.removeEventListener('keydown', handleKeydown, true)
      legalLinks.forEach((link) => {
        link.removeEventListener('click', handleLegalLinkClick)
      })
      root.remove()
    }

    document.addEventListener('keydown', handleKeydown, true)
    legalLinks.forEach((link) => {
      link.addEventListener('click', handleLegalLinkClick)
    })
    dialog?.focus({ preventScroll: true })

    acceptButton?.addEventListener('click', () => {
      if (isSettled) return
      isSettled = true
      markNativePrivacyConsentAccepted(storage)
      cleanup()
      resolve(true)
    })

    rejectButton?.addEventListener('click', async () => {
      if (isSettled) return
      isSettled = true
      if (acceptButton) acceptButton.disabled = true
      if (rejectButton) rejectButton.disabled = true
      root.classList.add('is-blocked')
      await exitApp()
      resolve(false)
    })
  })
}

export async function ensureNativePrivacyConsent(
  options: NativePrivacyConsentOptions = {}
): Promise<boolean> {
  const nativeRuntime = options.nativeRuntime ?? isNativeMobileRuntime()
  const storage = options.storage ?? getBrowserStorage()

  if (getNativePrivacyBootstrapState(nativeRuntime, storage) === 'ready') {
    return true
  }

  if (typeof document === 'undefined') return false

  return renderNativePrivacyConsentDialog(storage, options.exitApp ?? exitNativeApp)
}
