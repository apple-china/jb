import { reactive } from 'vue'
import { api, ApiError } from './api'
import type { CurrentUser } from './types'
import { isDingTalkEnvironment, requestDingTalkAuthCode } from './integrations/dingtalk'
import { diagnosticErrorCode, diagnosticId, diagnosticTraceId, recordDiagnostic } from './diagnostics'

const LOGOUT_SUPPRESS_KEY = 'jiabei-explicit-logout'
const DINGTALK_FAILURE_KEY = 'jiabei-dingtalk-failure'
let cachedUser: CurrentUser | null = null
let restoring: Promise<CurrentUser> | null = null
let dingTalkAuthentication: Promise<CurrentUser> | null = null
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
  return import.meta.env.DINGTALK_AUTO_LOGIN === 'true' && !explicitlyLoggedOut() && isDingTalkEnvironment()
}

export function isMockLoginEnabled() {
  const mode = import.meta.env.MODE
  if (mode === 'prod' || mode === 'production') return false
  const configured = import.meta.env.MOCK_LOGIN_ENABLED?.trim()
  if (configured) return configured === 'true'
  return mode !== 'dev'
}

export async function restoreSession() {
  if (cachedUser) return cachedUser
  if (!restoring) {
    recordDiagnostic('SESSION_RESTORE_STARTED')
    restoring = api.me()
      .then(user => { recordDiagnostic('SESSION_RESTORE_SUCCEEDED'); return markAuthenticated(user) })
      .catch(error => { recordDiagnostic('SESSION_RESTORE_FAILED', { errorCode: error }); throw error })
      .finally(() => { restoring = null })
  }
  return restoring
}

export function authenticateDingTalk() {
  if (dingTalkAuthentication) return dingTalkAuthentication
  const started=performance.now()
  recordDiagnostic('DINGTALK_AUTH_STARTED')
  const operation=(async()=>{
    try{
      const { authCode, corpId } = await requestDingTalkAuthCode()
      recordDiagnostic('BACKEND_LOGIN_STARTED',{elapsedMs:performance.now()-started})
      const user=markAuthenticated(await api.dingTalkLogin(authCode, corpId))
      recordDiagnostic('BACKEND_LOGIN_SUCCEEDED',{elapsedMs:performance.now()-started})
      recordDiagnostic('DINGTALK_AUTH_SUCCEEDED',{elapsedMs:performance.now()-started})
      return user
    }catch(error){
      recordDiagnostic('DINGTALK_AUTH_FAILED',{elapsedMs:performance.now()-started,errorCode:error})
      throw error
    }
  })()
  let shared:Promise<CurrentUser>
  shared=operation.finally(()=>{if(dingTalkAuthentication===shared)dingTalkAuthentication=null})
  dingTalkAuthentication=shared
  return shared
}

export type DingTalkFailureDetails = {
  code: string
  diagnosticId: string
  serverTraceId?: string
}

export function dingTalkFailureDetails(error:unknown):DingTalkFailureDetails{
  const serverTraceId=diagnosticTraceId(error)
  return {code:diagnosticErrorCode(error),diagnosticId:diagnosticId(),...(serverTraceId?{serverTraceId}:{})}
}

export function rememberDingTalkFailure(error:unknown){
  const details=dingTalkFailureDetails(error)
  try{sessionStorage.setItem(DINGTALK_FAILURE_KEY,JSON.stringify(details))}catch{/* Recovery remains available in memory for the current caller. */}
  return details
}

export function readDingTalkFailure():DingTalkFailureDetails|null{
  try{
    const value=JSON.parse(sessionStorage.getItem(DINGTALK_FAILURE_KEY)??'null') as Partial<DingTalkFailureDetails>|null
    if(!value||typeof value!=='object')return null
    const code=diagnosticErrorCode(value.code)
    const diagnosticIdValue=typeof value.diagnosticId==='string'&&/^[A-Za-z0-9-]{8,80}$/.test(value.diagnosticId)?value.diagnosticId:''
    if(!diagnosticIdValue)return null
    const serverTraceId=typeof value.serverTraceId==='string'&&/^[A-Za-z0-9-]{8,64}$/.test(value.serverTraceId)?value.serverTraceId:undefined
    return {code,diagnosticId:diagnosticIdValue,...(serverTraceId?{serverTraceId}:{})}
  }catch{return null}
}

export function clearDingTalkFailure(){
  try{sessionStorage.removeItem(DINGTALK_FAILURE_KEY)}catch{/* Storage is optional. */}
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
