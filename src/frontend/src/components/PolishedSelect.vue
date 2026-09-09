<script lang="ts">
let closeActiveSelect:(()=>void)|null=null
</script>

<script setup lang="ts">
import { ChevronDown, Check } from 'lucide-vue-next'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

export interface SelectOption { value: string; label: string; disabled?: boolean }
const props=withDefaults(defineProps<{modelValue:string;options:SelectOption[];placeholder?:string;ariaLabel?:string}>(),{placeholder:'请选择',ariaLabel:'请选择'})
const emit=defineEmits<{ 'update:modelValue':[value:string]; change:[value:string] }>()
const root=ref<HTMLElement|null>(null);const open=ref(false)
const selected=computed(()=>props.options.find(item=>item.value===props.modelValue))
function close(){open.value=false;if(closeActiveSelect===close)closeActiveSelect=null}
function toggle(){if(open.value){close();return}closeActiveSelect?.();open.value=true;closeActiveSelect=close}
function choose(value:string){emit('update:modelValue',value);emit('change',value);close()}
function outside(event:MouseEvent){if(root.value&&!root.value.contains(event.target as Node))close()}
function keyboard(event:KeyboardEvent){if(event.key==='Escape')close()}
onMounted(()=>{document.addEventListener('click',outside);document.addEventListener('keydown',keyboard)})
onBeforeUnmount(()=>{document.removeEventListener('click',outside);document.removeEventListener('keydown',keyboard);if(closeActiveSelect===close)closeActiveSelect=null})
</script>

<template>
  <div ref="root" class="smart-select" :class="{open}">
    <button type="button" class="smart-select-trigger" role="combobox" :aria-label="ariaLabel" :aria-expanded="open" aria-haspopup="listbox" @click.stop="toggle">
      <span class="smart-select-leading"><slot name="leading"/></span>
      <span :class="{placeholder:!selected}">{{ selected?.label||placeholder }}</span>
      <ChevronDown :size="17"/>
    </button>
    <Transition name="select-pop">
      <div v-if="open" class="smart-select-menu" role="listbox" :aria-label="ariaLabel">
        <button v-for="item in options" :key="item.value" type="button" role="option" :aria-selected="item.value===modelValue" :disabled="item.disabled" @click="choose(item.value)">
          <span>{{ item.label }}</span><Check v-if="item.value===modelValue" :size="16"/>
        </button>
      </div>
    </Transition>
  </div>
</template>
