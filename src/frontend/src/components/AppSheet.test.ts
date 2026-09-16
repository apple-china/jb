import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import AppSheet from './AppSheet.vue'

describe('AppSheet',()=>{
  it('renders an accessible modal, locks body scroll and emits close',async()=>{const wrapper=mount(AppSheet,{props:{open:true,title:'预约化妆'},slots:{default:'表单内容'},attachTo:document.body});expect(document.querySelector('[role="dialog"]')?.getAttribute('aria-label')).toBe('预约化妆');expect(document.body.style.overflow).toBe('hidden');await document.querySelector<HTMLButtonElement>('[aria-label="关闭"]')?.click();expect(wrapper.emitted('close')).toHaveLength(1);wrapper.unmount();expect(document.body.style.overflow).toBe('')})
  it('keeps scrolling locked until every stacked sheet closes',async()=>{const first=mount(AppSheet,{props:{open:true,title:'详情'},attachTo:document.body});const second=mount(AppSheet,{props:{open:true,title:'修改'},attachTo:document.body});expect(document.body.style.overflow).toBe('hidden');await second.setProps({open:false});expect(document.body.style.overflow).toBe('hidden');await first.setProps({open:false});expect(document.body.style.overflow).toBe('');first.unmount();second.unmount()})
})
