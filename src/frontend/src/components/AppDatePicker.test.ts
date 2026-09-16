import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import AppDatePicker from './AppDatePicker.vue'

describe('AppDatePicker',()=>{
  it('switches months and selects a date',async()=>{
    const wrapper=mount(AppDatePicker,{props:{modelValue:'2026-09-08',label:'开始日期'}})
    await wrapper.get('button.date-picker-trigger').trigger('click')
    expect(wrapper.text()).toContain('2026年 9月')
    await wrapper.get('button[aria-label="下个月"]').trigger('click')
    expect(wrapper.text()).toContain('2026年 10月')
    await wrapper.get('button[aria-label="2026-10-01"]').trigger('click')
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    await wrapper.get('.calendar-actions .confirm').trigger('click')
    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual(['2026-10-01'])
  })
  it('closes on Escape',async()=>{
    const wrapper=mount(AppDatePicker,{attachTo:document.body,props:{modelValue:'2026-09-08',label:'截止日期'}})
    await wrapper.get('button.date-picker-trigger').trigger('click')
    document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape'}))
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.calendar-popover').exists()).toBe(false)
    wrapper.unmount()
  })
})
