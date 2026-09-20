import { mount } from '@vue/test-utils'
import { beforeAll, describe, expect, it, vi } from 'vitest'
import TimeWheel from './TimeWheel.vue'

const slots=['11','12','13'].flatMap(hour=>['00','10','20','30','40','50'].map(minute=>({time:`${hour}:${minute}`,available:!(hour==='12'&&minute==='10')})))

describe('TimeWheel',()=>{
  beforeAll(()=>{HTMLElement.prototype.scrollTo=vi.fn()})
  it('hides unavailable minutes and keeps numeric minute order',async()=>{const wrapper=mount(TimeWheel,{props:{modelValue:'12:50',slots}});await wrapper.vm.$nextTick();expect(wrapper.find('[aria-label="分钟"] [data-value="10"]').exists()).toBe(false);expect(wrapper.findAll('[aria-label="分钟"] button').map(button=>button.text())).toEqual(['00','20','30','40','50']);await wrapper.find('[aria-label="小时"] [data-value="13"]').trigger('click');await wrapper.find('[aria-label="分钟"] [data-value="00"]').trigger('click');expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['13:00'])})
  it('renders one finite set of hours and minutes without loop copies',async()=>{const wrapper=mount(TimeWheel,{props:{modelValue:'13:50',slots}});await wrapper.vm.$nextTick();expect(wrapper.findAll('[aria-label="小时"] button')).toHaveLength(3);expect(wrapper.findAll('[aria-label="分钟"] button')).toHaveLength(6);expect(wrapper.find('[data-copy]').exists()).toBe(false)})
})
