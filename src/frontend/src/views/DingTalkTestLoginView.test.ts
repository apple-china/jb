import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, describe, expect, it, vi } from 'vitest'

describe('DingTalk test login',()=>{
  afterEach(()=>vi.unstubAllEnvs())

  it('keeps password and DingTalk login but hides every direct role login',async()=>{
    vi.stubEnv('VITE_ENABLE_MOCK_LOGIN','false')
    vi.resetModules()
    const {default:LoginView}=await import('./LoginView.vue')
    const router=createRouter({history:createMemoryHistory(),routes:[{path:'/login',component:LoginView}]})
    await router.push('/login');await router.isReady()
    const wrapper=mount(LoginView,{global:{plugins:[router]}})
    expect(wrapper.text()).toContain('加贝互娱')
    expect(wrapper.text()).toContain('账号登录')
    expect(wrapper.text()).not.toContain('可使用钉钉免登')
    expect(wrapper.text()).toContain('钉钉免登')
    expect(wrapper.findAll('.password-login input')).toHaveLength(2)
    expect(wrapper.find('.account-list').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('选择测试身份')
  })
})
