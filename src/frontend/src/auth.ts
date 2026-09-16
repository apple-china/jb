import { reactive } from 'vue'
import { api, ApiError } from './api'
import type { CurrentUser } from './types'
import { isDingTalkEnvironment, requestDingTalkAuthCode } from './integrations/dingtalk'

const LOGOUT_SUPPRESS_KEY = 'jiabei-explicit-logout'
let cachedUser: CurrentUser | null = null
let restoring: Promise<CurrentUser> | null = null
let messageTimer: number | undefined

export const authUi = reactive({ checking: false, message: '', kind: 'info' as 'info' | 'success' | 'warning' | 'error' })

export function destinationFor(user: CurrentUser, requested?: string) {
  const home = user.role === 'STREAMER' ? '/booking' : '/admin?tab=appointments'
  if (!requested || requested === '/' || requested.startsWith('/login')) return home
  if (user.role === 'STREAMER' && requested.startsWith('/booking')) return requested
  if (user.role !== 'STREAMER' && requested.startsWith('/admin')) return requested
  return home
}

export function markAuthenticated(user: CurrentUser) {
  cachedUser = user
  sessionStorage.setItem('jiabei-had-session', '1')
  sessionStorage.removeItem(LOGOUT_SUPPRESS_KEY)
  return user
}

export function clearAuthenticated() {
  cachedUser = null
  sessionStorage.removeItem('jiabei-csrf')
}

export function explicitlyLoggedOut() {
  return sessionStorage.getItem(LOGOUT_SUPPRESS_KEY) === '1'
}

export function canAutoDingTalkLogin() {
  return import.meta.env.VITE_DINGTALK_AUTO_LOGIN !== 'false' && !explicitlyLoggedOut() && isDingTalkEnvironment()
}

export async function restoreSession() {
  if (cachedUser) return cachedUser
  if (!restoring) restoring = api.me().then(markAuthenticated).finally(() => { restoring = null })
  return restoring
}

export async function authenticateDingTalk() {
  const { authCode, corpId } = await requestDingTalkAuthCode()
  return markAuthenticated(await api.dingTalkLogin(authCode, corpId))
}

export async function signOut() {
  try { await api.logout() } finally {
    clearAuthenticated()
    sessionStorage.setItem(LOGOUT_SUPPRESS_KEY, '1')
  }
}

export function showAuthMessage(message: string, kind: typeof authUi.kind = 'info', duration = 3000) {
  authUi.message = message
  authUi.kind = kind
  if (messageTimer) window.clearTimeout(messageTimer)
  messageTimer = window.setTimeout(() => { authUi.message = '' }, duration)
}

export function isSessionError(error: unknown) {
  return error instanceof ApiError && (error.status === 401 || error.code === 'SESSION_INVALIDATED')
}
