import type { App } from 'vue'

const ID_KEY = 'jiabei-diagnostic-id'
const EVENTS_KEY = 'jiabei-diagnostic-events'
const MAX_EVENTS = 20
const startedAt = typeof performance === 'undefined' ? 0 : performance.now()
let memoryId = ''

type DiagnosticListeners = {
  error: EventListener
  unhandledRejection: EventListener
}

type DiagnosticWindow = Window & {
  __jiabeiDiagnosticListenersV1__?: DiagnosticListeners
}

export type DiagnosticStage =
  | 'MODULE_STARTED'
  | 'APP_MOUNTED'
  | 'ROUTER_STARTED'
  | 'SESSION_RESTORE_STARTED'
  | 'SESSION_RESTORE_SUCCEEDED'
  | 'SESSION_RESTORE_FAILED'
  | 'DINGTALK_AUTH_STARTED'
  | 'DINGTALK_AUTH_SUCCEEDED'
  | 'DINGTALK_AUTH_FAILED'
  | 'BACKEND_LOGIN_STARTED'
  | 'BACKEND_LOGIN_SUCCEEDED'
  | 'WINDOW_ERROR'
  | 'UNHANDLED_REJECTION'
  | 'VUE_ERROR'

const SAFE_CODES = new Set([
  'ACCOUNT_DISABLED', 'ACCOUNT_UNREGISTERED', 'DINGTALK_AUTH_FAILED',
  'DINGTALK_AUTH_TIMEOUT', 'DINGTALK_CODE_EMPTY', 'DINGTALK_CORP_MISMATCH',
  'DINGTALK_NOT_CONFIGURED', 'DINGTALK_UNAVAILABLE', 'FORBIDDEN', 'JS_ERROR',
  'NETWORK_ERROR', 'NOT_IN_DINGTALK', 'PROMISE_REJECTION', 'UNAUTHORIZED',
  'UNKNOWN_ERROR', 'VUE_ERROR',
])

type DiagnosticEvent = {
  stage: DiagnosticStage
  version: 1
  elapsed: '<1s' | '1-5s' | '5-10s' | '10s+'
  errorCode?: string
}

function createDiagnosticId() {
  try {
    if (typeof globalThis.crypto?.randomUUID === 'function') return globalThis.crypto.randomUUID()
  } catch { /* Continue with the correlation-only fallback. */ }
  try {
    if (typeof globalThis.crypto?.getRandomValues === 'function') {
      const bytes = globalThis.crypto.getRandomValues(new Uint8Array(16))
      return `diag-${Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join('')}`
    }
  } catch { /* Continue with the non-security fallback. */ }
  // This identifier only correlates local diagnostics; it is never an auth or security input.
  return `diag-fallback-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12) || '0'}`
}

export function diagnosticId() {
  if (memoryId) return memoryId
  try {
    const stored = sessionStorage.getItem(ID_KEY)
    if (stored) return (memoryId = stored)
    memoryId = createDiagnosticId()
    sessionStorage.setItem(ID_KEY, memoryId)
    return memoryId
  } catch {
    return (memoryId ||= createDiagnosticId())
  }
}

export function diagnosticErrorCode(error: unknown) {
  const candidate = typeof error === 'string'
    ? error
    : typeof error === 'object' && error && 'code' in error
      ? String((error as { code?:unknown }).code ?? '')
      : ''
  return SAFE_CODES.has(candidate) ? candidate : 'UNKNOWN_ERROR'
}

export function diagnosticTraceId(error?: unknown) {
  const trace = typeof error === 'object' && error && 'traceId' in error
    ? String((error as { traceId?:unknown }).traceId ?? '')
    : ''
  return /^[A-Za-z0-9-]{8,64}$/.test(trace) ? trace : undefined
}

function elapsedBucket(elapsedMs: number): DiagnosticEvent['elapsed'] {
  if (elapsedMs < 1_000) return '<1s'
  if (elapsedMs < 5_000) return '1-5s'
  if (elapsedMs < 10_000) return '5-10s'
  return '10s+'
}

export function recordDiagnostic(stage: DiagnosticStage, details: { elapsedMs?:number; errorCode?:unknown } = {}) {
  const event: DiagnosticEvent = {
    stage,
    version: 1,
    elapsed: elapsedBucket(details.elapsedMs ?? Math.max(0, performance.now() - startedAt)),
  }
  if (details.errorCode !== undefined) event.errorCode = diagnosticErrorCode(details.errorCode)
  try {
    const existing = JSON.parse(sessionStorage.getItem(EVENTS_KEY) ?? '[]')
    const events = Array.isArray(existing) ? existing.slice(-(MAX_EVENTS - 1)) : []
    events.push(event)
    sessionStorage.setItem(EVENTS_KEY, JSON.stringify(events))
  } catch { /* Diagnostics must never block login. */ }
  return event
}

export function diagnosticSnapshot(): readonly DiagnosticEvent[] {
  try {
    const parsed = JSON.parse(sessionStorage.getItem(EVENTS_KEY) ?? '[]')
    return Array.isArray(parsed) ? parsed : []
  } catch { return [] }
}

export function installBrowserDiagnostics(app: App) {
  const diagnosticWindow = window as DiagnosticWindow
  const previous = diagnosticWindow.__jiabeiDiagnosticListenersV1__
  if (previous) {
    window.removeEventListener('error', previous.error)
    window.removeEventListener('unhandledrejection', previous.unhandledRejection)
  }
  const error: EventListener = () => recordDiagnostic('WINDOW_ERROR', { errorCode: 'JS_ERROR' })
  const unhandledRejection: EventListener = () => recordDiagnostic('UNHANDLED_REJECTION', { errorCode: 'PROMISE_REJECTION' })
  window.addEventListener('error', error)
  window.addEventListener('unhandledrejection', unhandledRejection)
  diagnosticWindow.__jiabeiDiagnosticListenersV1__ = { error, unhandledRejection }
  app.config.errorHandler = () => recordDiagnostic('VUE_ERROR', { errorCode: 'VUE_ERROR' })
}
