import { beforeEach, describe, expect, it, vi } from 'vitest'
import { diagnosticId, diagnosticSnapshot, installBrowserDiagnostics, recordDiagnostic } from './diagnostics'

describe('local WebView diagnostics', () => {
  beforeEach(() => sessionStorage.clear())

  it('keeps one random identifier for the browser session', () => {
    const first = diagnosticId()
    expect(first).toMatch(/^[0-9a-f-]{36}$/)
    expect(diagnosticId()).toBe(first)
  })

  it('stores only fixed, redacted diagnostic fields', () => {
    recordDiagnostic('DINGTALK_AUTH_FAILED', {
      elapsedMs: 12_345,
      errorCode: 'DINGTALK_AUTH_TIMEOUT',
      ignored: 'authCode=secret-token user=someone stack=private',
    } as never)

    const serialized = JSON.stringify(diagnosticSnapshot())
    expect(serialized).toContain('DINGTALK_AUTH_TIMEOUT')
    expect(serialized).toContain('10s+')
    expect(serialized).not.toMatch(/secret-token|someone|private|authCode|stack/)
  })

  it('reduces browser and Vue failures to fixed error categories', () => {
    const app = { config: {} } as never
    installBrowserDiagnostics(app)
    window.dispatchEvent(new ErrorEvent('error', { message: 'private stack and user data' }))
    ;(app as {config:{errorHandler:(error:unknown)=>void}}).config.errorHandler(new Error('private'))
    const serialized = JSON.stringify(diagnosticSnapshot())
    expect(serialized).toContain('JS_ERROR')
    expect(serialized).toContain('VUE_ERROR')
    expect(serialized).not.toMatch(/private stack|user data/)
  })

  it('keeps only one window listener after a simulated module reload', async () => {
    const first = await import('./diagnostics')
    first.installBrowserDiagnostics({ config: {} } as never)
    vi.resetModules()
    const reloaded = await import('./diagnostics')
    reloaded.installBrowserDiagnostics({ config: {} } as never)
    const before = reloaded.diagnosticSnapshot().length
    window.dispatchEvent(new ErrorEvent('error', { message: 'must not be stored' }))
    const added = reloaded.diagnosticSnapshot().slice(before)
    expect(added).toHaveLength(1)
    expect(added[0]).toMatchObject({ stage: 'WINDOW_ERROR', errorCode: 'JS_ERROR' })
  })
})
