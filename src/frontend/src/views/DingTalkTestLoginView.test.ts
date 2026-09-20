import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, describe, expect, it, vi } from 'vitest'

async function mountLogin(mode:string,mockLogin?:string){
  vi.stubEnv('MODE',mode)
  if(mockLogin!==undefined)vi.stubEnv('MOCK_LOGIN_ENABLED',mockLogin)
  vi.resetModules()
  const {default:LoginView}=await import('./LoginView.vue')
  const router=createRouter({history:createMemoryHistory(),routes:[{path:'/login',component:LoginView}]})
  await router.push('/login');await router.isReady()
  return mount(LoginView,{global:{plugins:[router]}})
}

describe('DingTalk test login',()=>{
  afterEach(()=>vi.unstubAllEnvs())

  it('keeps password and DingTalk login but hides every direct role login',async()=>{
    const wrapper=await mountLogin('dev','false')
    expect(wrapper.text()).toContain('加贝互娱')
    expect(wrapper.text()).not.toContain('账号登录')
    expect(wrapper.text()).not.toContain('可使用钉钉免登')
    expect(wrapper.text()).toContain('钉钉免登')
    expect(wrapper.findAll('.password-login input')).toHaveLength(2)
    expect(wrapper.find('.account-list').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('选择测试身份')
  })

  it('allows the independent Mock login switch in dev mode',async()=>{
    const wrapper=await mountLogin('dev','true')
    expect(wrapper.find('.account-list').exists()).toBe(true)
  })

  it('always blocks Mock login in prod modes',async()=>{
    for(const mode of ['prod','production']){
      const wrapper=await mountLogin(mode,'true')
      expect(wrapper.find('.account-list').exists()).toBe(false)
    }
  })

  it('keeps Mock login enabled for an unconfigured local development mode',async()=>{
    const wrapper=await mountLogin('development')
    expect(wrapper.find('.account-list').exists()).toBe(true)
  })
})
