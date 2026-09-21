import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { canAutoDingTalkLogin } from './auth'

describe('DingTalk automatic login switch', () => {
  beforeEach(() => {
    sessionStorage.clear()
    window.dd = { requestAuthCode: vi.fn() }
    vi.stubEnv('DINGTALK_CLIENT_ID', 'configured-client')
  })

  afterEach(() => {
    delete window.dd
    vi.unstubAllEnvs()
  })

  it('allows automatic login when the switch is true', () => {
    vi.stubEnv('DINGTALK_AUTO_LOGIN', 'true')
    expect(canAutoDingTalkLogin()).toBe(true)
  })

  it('blocks automatic login when the switch is false even with a configured Client ID', () => {
    vi.stubEnv('DINGTALK_AUTO_LOGIN', 'false')
    expect(canAutoDingTalkLogin()).toBe(false)
  })
})
