import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { describe, expect, it } from 'vitest'
import LoginView from './LoginView.vue'

describe('LoginView',()=>{
  it('shows all five fixed roles and password login',async()=>{const router=createRouter({history:createMemoryHistory(),routes:[{path:'/',component:LoginView}]});await router.push('/');await router.isReady();const wrapper=mount(LoginView,{global:{plugins:[router]}});for(const role of ['超管','运营','观察员','化妆师','主播'])expect(wrapper.text()).toContain(role);expect(wrapper.find('.password-login h2').text()).toBe('账号登录');expect(wrapper.text()).not.toContain('化妆预约')})
  it('enables password login only for 6-12 character alphanumeric accounts and passwords',async()=>{const router=createRouter({history:createMemoryHistory(),routes:[{path:'/',component:LoginView}]});await router.push('/');await router.isReady();const wrapper=mount(LoginView,{global:{plugins:[router]}});const inputs=wrapper.findAll('.password-login input'),button=wrapper.find('.login-submit');await inputs[0].setValue('12345');await inputs[1].setValue('123456');expect(button.attributes('disabled')).toBeDefined();await inputs[0].setValue(' Abc123 ');expect(button.attributes('disabled')).toBeUndefined();await inputs[0].setValue('abc_123');expect(button.attributes('disabled')).toBeDefined();await inputs[0].setValue('abcdefghijklm');expect(button.attributes('disabled')).toBeDefined();await inputs[0].setValue('Abc123');await inputs[1].setValue('12345');expect(button.attributes('disabled')).toBeDefined();await inputs[1].setValue('abcdefghijklm');expect(button.attributes('disabled')).toBeDefined()})
})
