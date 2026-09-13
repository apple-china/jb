import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import AppToast from '../components/AppToast.vue'
import { useAppToast } from './useAppToast'

const Host=defineComponent({components:{AppToast},setup(){return useAppToast()},template:'<AppToast :message="toast.message" :kind="toast.kind"/>'})

describe('useAppToast',()=>{
  afterEach(()=>vi.useRealTimers())
  it('renders every kind and replaces then hides feedback after one second',async()=>{vi.useFakeTimers();const wrapper=mount(Host);for(const kind of ['info','success','warning','error'] as const){(wrapper.vm as any).showToast(kind,kind);await wrapper.vm.$nextTick();expect(document.body.querySelector(`.app-toast-${kind}`)?.textContent).toContain(kind)}vi.advanceTimersByTime(900);(wrapper.vm as any).showToast('新的提示','success');vi.advanceTimersByTime(999);await wrapper.vm.$nextTick();expect(document.body.textContent).toContain('新的提示');vi.advanceTimersByTime(1);await wrapper.vm.$nextTick();expect(document.body.textContent).not.toContain('新的提示');wrapper.unmount()})
  it('supports a per-message duration for DingTalk diagnostics',async()=>{vi.useFakeTimers();const wrapper=mount(Host);(wrapper.vm as any).showToast('免登诊断','success',3000);vi.advanceTimersByTime(2999);await wrapper.vm.$nextTick();expect(document.body.textContent).toContain('免登诊断');vi.advanceTimersByTime(1);await wrapper.vm.$nextTick();expect(document.body.textContent).not.toContain('免登诊断');wrapper.unmount()})
})
