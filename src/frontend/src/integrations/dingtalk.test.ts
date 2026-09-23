import { afterEach, describe, expect, it, vi } from 'vitest'

const sdk = vi.hoisted(() => ({
  env: { platform: 'notInDingTalk' },
  requestAuthCode: vi.fn(),
}))
vi.mock('dingtalk-jsapi', () => sdk)

import { DINGTALK_AUTH_TIMEOUT_MS, requestDingTalkAuthCode } from './dingtalk'

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllEnvs()
  sdk.env.platform = 'notInDingTalk'
  sdk.requestAuthCode.mockReset()
  delete window.dd
})

describe('requestDingTalkAuthCode', () => {
  it('rejects cleanly outside DingTalk', async () => {
    await expect(requestDingTalkAuthCode()).rejects.toMatchObject({
      code: 'NOT_IN_DINGTALK',
    })
  })

  it('passes public configuration to an injected JSAPI and returns its one-time code', async () => {
    vi.stubEnv('DINGTALK_CLIENT_ID', 'client-public')
    vi.stubEnv('DINGTALK_CORP_ID', 'corp-public')
    const requestAuthCode = vi.fn((options:{success:(result:{code?:string})=>void}) => options.success({ code: 'one-time-code' }))
    window.dd = { requestAuthCode }

    await expect(requestDingTalkAuthCode()).resolves.toEqual({
      authCode: 'one-time-code',
      corpId: 'corp-public',
    })
    expect(requestAuthCode).toHaveBeenCalledWith(expect.objectContaining({
      clientId: 'client-public',
      corpId: 'corp-public',
    }))
  })

  it('uses the installed DingTalk SDK when the client does not inject window.dd', async () => {
    vi.stubEnv('DINGTALK_CLIENT_ID', 'client-public')
    vi.stubEnv('DINGTALK_CORP_ID', 'corp-public')
    sdk.env.platform = 'ios'
    sdk.requestAuthCode.mockResolvedValue({ code: 'sdk-one-time-code' })

    await expect(requestDingTalkAuthCode()).resolves.toEqual({
      authCode: 'sdk-one-time-code',
      corpId: 'corp-public',
    })
    expect(sdk.requestAuthCode).toHaveBeenCalledWith({
      clientId: 'client-public',
      corpId: 'corp-public',
    })
  })

  it('does not retry when an injected JSAPI fails', async () => {
    vi.stubEnv('DINGTALK_CLIENT_ID', 'client-public')
    vi.stubEnv('DINGTALK_CORP_ID', 'corp-public')
    const requestAuthCode = vi.fn((options:{fail:(error:unknown)=>void}) => options.fail(new Error('denied')))
    window.dd = { requestAuthCode }

    await expect(requestDingTalkAuthCode()).rejects.toMatchObject({ code: 'DINGTALK_AUTH_FAILED' })
    expect(requestAuthCode).toHaveBeenCalledTimes(1)
  })

  it.each(['callback', 'promise'] as const)('times out a permanently pending %s authorization', async kind => {
    vi.useFakeTimers()
    vi.stubEnv('DINGTALK_CLIENT_ID', 'client-public')
    vi.stubEnv('DINGTALK_CORP_ID', 'corp-public')
    if (kind === 'callback') window.dd = { requestAuthCode: vi.fn() }
    else {
      sdk.env.platform = 'android'
      sdk.requestAuthCode.mockReturnValue(new Promise(() => {}))
    }

    const authorization = requestDingTalkAuthCode()
    const timedOut = expect(authorization).rejects.toMatchObject({ code: 'DINGTALK_AUTH_TIMEOUT' })
    await vi.advanceTimersByTimeAsync(DINGTALK_AUTH_TIMEOUT_MS)

    await timedOut
    vi.useRealTimers()
  })

  it('ignores a callback arriving after the timeout has settled the promise', async () => {
    vi.useFakeTimers()
    vi.stubEnv('DINGTALK_CLIENT_ID', 'client-public')
    vi.stubEnv('DINGTALK_CORP_ID', 'corp-public')
    let lateSuccess: ((result:{code?:string})=>void) | undefined
    window.dd = { requestAuthCode: vi.fn(options => { lateSuccess = options.success }) }

    const authorization = requestDingTalkAuthCode()
    const timedOut = expect(authorization).rejects.toMatchObject({ code: 'DINGTALK_AUTH_TIMEOUT' })
    await vi.advanceTimersByTimeAsync(DINGTALK_AUTH_TIMEOUT_MS)
    await timedOut
    expect(() => lateSuccess?.({ code: 'too-late' })).not.toThrow()
    await expect(authorization).rejects.toMatchObject({ code: 'DINGTALK_AUTH_TIMEOUT' })
    vi.useRealTimers()
  })
})
