import { mount } from '@vue/test-utils'
import { defineComponent, ref } from 'vue'
import { describe, expect, it } from 'vitest'
import PolishedSelect from './PolishedSelect.vue'

const Host=defineComponent({components:{PolishedSelect},setup(){return {a:ref(''),b:ref(''),options:[{value:'1',label:'选项一'}]}},template:'<PolishedSelect v-model="a" :options="options" aria-label="第一个"/><PolishedSelect v-model="b" :options="options" aria-label="第二个"/>'})

describe('PolishedSelect',()=>{
  it('keeps only one custom select open',async()=>{const wrapper=mount(Host,{attachTo:document.body});const triggers=wrapper.findAll('[role="combobox"]');await triggers[0].trigger('click');expect(triggers[0].attributes('aria-expanded')).toBe('true');await triggers[1].trigger('click');expect(triggers[0].attributes('aria-expanded')).toBe('false');expect(triggers[1].attributes('aria-expanded')).toBe('true');wrapper.unmount()})
  it('does not open while disabled',async()=>{const wrapper=mount(PolishedSelect,{props:{modelValue:'1',options:[{value:'1',label:'08:00'}],disabled:true,ariaLabel:'上班时间'},attachTo:document.body});const trigger=wrapper.get('[role="combobox"]');expect(trigger.attributes('disabled')).toBeDefined();await trigger.trigger('click');expect(trigger.attributes('aria-expanded')).toBe('false');expect(wrapper.find('[role="listbox"]').exists()).toBe(false);wrapper.unmount()})
})
