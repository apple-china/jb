import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from './api'

afterEach(() => vi.unstubAllGlobals())

describe('API trace preservation', () => {
  it('keeps a server trace ID on a failed response for safe user diagnostics', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      success: false,
      error: { code: 'DINGTALK_UNAVAILABLE', message: '暂时不可用' },
      traceId: 'server-trace-1234',
    }), { status: 502, headers: { 'Content-Type': 'application/json' } })))

    await expect(api.me()).rejects.toMatchObject({
      code: 'DINGTALK_UNAVAILABLE',
      traceId: 'server-trace-1234',
    })
  })
})
