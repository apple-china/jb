import { afterEach, describe, expect, it, vi } from 'vitest'

const sdk = vi.hoisted(() => ({
  env: { platform: 'notInDingTalk' },
  requestAuthCode: vi.fn(),
}))
vi.mock('dingtalk-jsapi', () => sdk)

import { missingAuthCodeMessage, requestDingTalkAuthCode } from './dingtalk'

afterEach(() => {
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
    vi.stubEnv('VITE_DINGTALK_CLIENT_ID', 'client-public')
    vi.stubEnv('VITE_DINGTALK_CORP_ID', 'corp-public')
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
    vi.stubEnv('VITE_DINGTALK_CLIENT_ID', 'client-public')
    vi.stubEnv('VITE_DINGTALK_CORP_ID', 'corp-public')
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

  it('formats the missing auth code diagnostic with time and corp id', () => {
    expect(missingAuthCodeMessage('corp-public', new Date(2026, 8, 14, 9, 8, 7)))
      .toBe('09:08:07 未获取到免登码:corp-public')
  })

  it('does not retry when an injected JSAPI fails', async () => {
    vi.stubEnv('VITE_DINGTALK_CLIENT_ID', 'client-public')
    vi.stubEnv('VITE_DINGTALK_CORP_ID', 'corp-public')
    const requestAuthCode = vi.fn((options:{fail:(error:unknown)=>void}) => options.fail(new Error('denied')))
    window.dd = { requestAuthCode }

    await expect(requestDingTalkAuthCode()).rejects.toMatchObject({ code: 'DINGTALK_AUTH_FAILED' })
    expect(requestAuthCode).toHaveBeenCalledTimes(1)
  })
})
