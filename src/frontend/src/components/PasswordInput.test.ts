import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import PasswordInput from './PasswordInput.vue'

describe('PasswordInput', () => {
  it('reveals and hides only its own password', async () => {
    const wrapper = mount(PasswordInput, {
      props: { modelValue: 'secret12', placeholder: '密码', autocomplete: 'current-password' },
    })
    const input = wrapper.get('input')
    const toggle = wrapper.get('button')

    expect(input.attributes('type')).toBe('password')
    expect(toggle.attributes('aria-label')).toBe('显示密码')
    await toggle.trigger('click')
    expect(input.attributes('type')).toBe('text')
    expect(toggle.attributes('aria-label')).toBe('隐藏密码')
    await toggle.trigger('click')
    expect(input.attributes('type')).toBe('password')
  })
})