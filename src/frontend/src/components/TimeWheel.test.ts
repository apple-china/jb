import { mount } from '@vue/test-utils'
import { beforeAll, describe, expect, it, vi } from 'vitest'
import TimeWheel from './TimeWheel.vue'

const slots=['11','12','13'].flatMap(hour=>['00','10','20','30','40','50'].map(minute=>({time:`${hour}:${minute}`,available:!(hour==='12'&&minute==='10')})))

describe('TimeWheel',()=>{
  beforeAll(()=>{HTMLElement.prototype.scrollTo=vi.fn()})
  it('keeps conflicts disabled and rolls minutes across adjacent hours',async()=>{const wrapper=mount(TimeWheel,{props:{modelValue:'12:50',slots}});await wrapper.vm.$nextTick();expect(wrapper.find('[data-copy="1"][data-value="10"]').attributes('disabled')).toBeDefined();await wrapper.find('[data-copy="2"][data-value="00"]').trigger('click');expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['13:00']);await wrapper.setProps({modelValue:'12:00'});await wrapper.find('[data-copy="0"][data-value="50"]').trigger('click');expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['11:50'])})
  it('does not loop hours or minutes past the service boundaries',async()=>{const wrapper=mount(TimeWheel,{props:{modelValue:'13:50',slots}});await wrapper.vm.$nextTick();expect(wrapper.findAll('[aria-label="小时"] button')).toHaveLength(3);expect(wrapper.find('[aria-label="小时"] [data-copy]').exists()).toBe(false);expect(wrapper.find('[aria-label="分钟"] [data-copy="2"][data-value="00"]').attributes('disabled')).toBeDefined();await wrapper.setProps({modelValue:'11:00'});expect(wrapper.find('[aria-label="分钟"] [data-copy="0"][data-value="50"]').attributes('disabled')).toBeDefined()})
})
