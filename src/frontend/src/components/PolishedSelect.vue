<script lang="ts">
let closeActiveSelect:(()=>void)|null=null
</script>

<script setup lang="ts">
import { ChevronDown, Check } from 'lucide-vue-next'
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'

export interface SelectOption { value: string; label: string; disabled?: boolean }
const props=withDefaults(defineProps<{modelValue:string;options:SelectOption[];placeholder?:string;ariaLabel?:string;disabled?:boolean}>(),{placeholder:'请选择',ariaLabel:'请选择',disabled:false})
const emit=defineEmits<{ 'update:modelValue':[value:string]; change:[value:string] }>()
const root=ref<HTMLElement|null>(null),menu=ref<HTMLElement|null>(null),open=ref(false),dropUp=ref(false)
const menuStyle=ref<Record<string,string>>({})
const selected=computed(()=>props.options.find(item=>item.value===props.modelValue))
function close(){open.value=false;if(closeActiveSelect===close)closeActiveSelect=null}
async function toggle(){if(props.disabled)return;if(open.value){close();return}closeActiveSelect?.();open.value=true;closeActiveSelect=close;await nextTick();positionMenu()}
function choose(value:string){emit('update:modelValue',value);emit('change',value);close()}
function positionMenu(){
  if(!open.value||!root.value||!menu.value)return
  const rect=root.value.getBoundingClientRect(),gap=5,edge=8,viewport=window.innerHeight
  const below=Math.max(0,viewport-rect.bottom-edge-gap),above=Math.max(0,rect.top-edge-gap)
  const preferred=Math.min(260,Math.max(120,menu.value.scrollHeight))
  dropUp.value=below<preferred&&above>below
  const available=dropUp.value?above:below,maxHeight=Math.max(80,Math.min(260,available))
  const height=Math.min(menu.value.scrollHeight,maxHeight)
  const left=Math.min(Math.max(edge,rect.left),Math.max(edge,window.innerWidth-rect.width-edge))
  menuStyle.value={position:'fixed',left:`${left}px`,right:'auto',width:`${rect.width}px`,maxHeight:`${maxHeight}px`,top:`${dropUp.value?Math.max(edge,rect.top-gap-height):rect.bottom+gap}px`}
}
function outside(event:MouseEvent){const target=event.target as Node;if(root.value&&!root.value.contains(target)&&!menu.value?.contains(target))close()}
function keyboard(event:KeyboardEvent){if(event.key==='Escape')close()}
onMounted(()=>{document.addEventListener('click',outside);document.addEventListener('keydown',keyboard);window.addEventListener('resize',positionMenu);window.addEventListener('scroll',positionMenu,true)})
onBeforeUnmount(()=>{document.removeEventListener('click',outside);document.removeEventListener('keydown',keyboard);window.removeEventListener('resize',positionMenu);window.removeEventListener('scroll',positionMenu,true);if(closeActiveSelect===close)closeActiveSelect=null})
</script>

<template>
  <div ref="root" class="smart-select" :class="{open,disabled}">
    <button type="button" class="smart-select-trigger" role="combobox" :aria-label="ariaLabel" :aria-expanded="open" aria-haspopup="listbox" :disabled="disabled" @click.stop="toggle">
      <span class="smart-select-leading"><slot name="leading"/></span>
      <span :class="{placeholder:!selected}">{{ selected?.label||placeholder }}</span>
      <ChevronDown :size="17"/>
    </button>
    <Teleport to="body">
      <Transition name="select-pop">
        <div v-if="open" ref="menu" class="smart-select-menu smart-select-floating" :class="{'drop-up':dropUp}" :style="menuStyle" role="listbox" :aria-label="ariaLabel">
          <button v-for="item in options" :key="item.value" type="button" role="option" :aria-selected="item.value===modelValue" :disabled="item.disabled" @click="choose(item.value)">
            <span>{{ item.label }}</span><Check v-if="item.value===modelValue" :size="16"/>
          </button>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>
