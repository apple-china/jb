import { afterEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  requestDingTalkAuthCode: vi.fn(),
  dingTalkLogin: vi.fn(),
}))

vi.mock('./integrations/dingtalk', () => ({
  isDingTalkEnvironment: () => true,
  requestDingTalkAuthCode: mocks.requestDingTalkAuthCode,
}))
vi.mock('./api', () => ({
  ApiError: class ApiError extends Error {},
  api: { me: vi.fn(), logout: vi.fn(), dingTalkLogin: mocks.dingTalkLogin },
}))

import { authenticateDingTalk } from './auth'

afterEach(() => vi.clearAllMocks())

describe('DingTalk authentication single-flight', () => {
  it('reuses one complete authentication task for concurrent callers', async () => {
    let resolveCode!: (value:{authCode:string;corpId:string})=>void
    mocks.requestDingTalkAuthCode.mockReturnValue(new Promise(resolve => { resolveCode = resolve }))
    mocks.dingTalkLogin.mockResolvedValue({ userId: 'user-1' })

    const first = authenticateDingTalk()
    const second = authenticateDingTalk()
    expect(second).toBe(first)
    resolveCode({ authCode: 'one-time', corpId: 'corp-public' })

    await expect(first).resolves.toMatchObject({ userId: 'user-1' })
    expect(mocks.requestDingTalkAuthCode).toHaveBeenCalledTimes(1)
    expect(mocks.dingTalkLogin).toHaveBeenCalledTimes(1)
  })

  it('clears a failed task so a manual retry can succeed', async () => {
    mocks.requestDingTalkAuthCode
      .mockRejectedValueOnce(Object.assign(new Error('timeout'), { code: 'DINGTALK_AUTH_TIMEOUT' }))
      .mockResolvedValueOnce({ authCode: 'retry-code', corpId: 'corp-public' })
    mocks.dingTalkLogin.mockResolvedValue({ userId: 'user-1' })

    await expect(authenticateDingTalk()).rejects.toMatchObject({ code: 'DINGTALK_AUTH_TIMEOUT' })
    await expect(authenticateDingTalk()).resolves.toMatchObject({ userId: 'user-1' })
    expect(mocks.requestDingTalkAuthCode).toHaveBeenCalledTimes(2)
    expect(mocks.dingTalkLogin).toHaveBeenCalledTimes(1)
  })
})
