import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

beforeEach(() => {
  sessionStorage.clear()
  vi.resetModules()
})
afterEach(() => vi.unstubAllGlobals())

describe('diagnostic identifier fallbacks', () => {
  it('uses getRandomValues when randomUUID is unavailable', async () => {
    vi.stubGlobal('crypto', { getRandomValues: (values:Uint8Array) => { values.fill(0x2a); return values } })
    const { diagnosticId } = await import('./diagnostics')
    expect(diagnosticId()).toMatch(/^diag-[0-9a-f]{32}$/)
  })

  it('uses getRandomValues when randomUUID throws', async () => {
    vi.stubGlobal('crypto', {
      randomUUID: () => { throw new Error('unsupported') },
      getRandomValues: (values:Uint8Array) => { values.fill(0x1b); return values },
    })
    const { diagnosticId } = await import('./diagnostics')
    expect(diagnosticId()).toMatch(/^diag-[0-9a-f]{32}$/)
  })

  it('never throws when both crypto APIs are unavailable', async () => {
    vi.stubGlobal('crypto', {})
    const { diagnosticId } = await import('./diagnostics')
    expect(() => diagnosticId()).not.toThrow()
    expect(diagnosticId()).toMatch(/^diag-fallback-[a-z0-9-]+$/)
  })
})
