import { mount } from '@vue/test-utils'
import { describe,expect,it } from 'vitest'
import AvatarImage from './AvatarImage.vue'

describe('AvatarImage',()=>{
  it('falls back to the default image and then to an initial',async()=>{
    const wrapper=mount(AvatarImage,{props:{src:'/broken.png',fallback:'/default.png',name:'小贝'}})
    expect(wrapper.get('img').attributes('src')).toBe('/broken.png')
    await wrapper.get('img').trigger('error')
    expect(wrapper.get('img').attributes('src')).toBe('/default.png')
    await wrapper.get('img').trigger('error')
    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.text()).toBe('小')
  })
})
