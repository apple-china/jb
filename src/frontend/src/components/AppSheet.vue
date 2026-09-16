<script setup lang="ts">
import { onBeforeUnmount, watch } from 'vue'
import { X } from 'lucide-vue-next'
const props=defineProps<{ open: boolean; title: string; subtitle?: string; fullscreen?: boolean }>()
defineEmits<{ close: [] }>()
const lockToken=Symbol('app-sheet')
const lockState: {tokens:Set<symbol>;originalOverflow:string} = ((globalThis as typeof globalThis & {__jiabeiSheetLockState?:{tokens:Set<symbol>;originalOverflow:string}}).__jiabeiSheetLockState ??= {tokens:new Set(),originalOverflow:''})
function setScrollLock(open:boolean){
  if(open){
    if(lockState.tokens.has(lockToken))return
    if(lockState.tokens.size===0)lockState.originalOverflow=document.body.style.overflow
    lockState.tokens.add(lockToken);document.body.style.overflow='hidden'
    return
  }
  lockState.tokens.delete(lockToken)
  if(lockState.tokens.size===0)document.body.style.overflow=lockState.originalOverflow
}
watch(()=>props.open,setScrollLock,{immediate:true})
onBeforeUnmount(()=>setScrollLock(false))
</script>
<template>
  <Teleport to="body">
    <div v-if="open" class="sheet-backdrop" @click.self="$emit('close')">
      <section class="sheet" :class="{ fullscreen }" role="dialog" aria-modal="true" :aria-label="title">
        <header><div class="sheet-heading"><h2>{{ title }}</h2><small v-if="subtitle">{{ subtitle }}</small></div><button class="icon-button" aria-label="关闭" @click="$emit('close')"><X :size="20" /></button></header>
        <div class="sheet-body"><slot /></div>
        <footer v-if="$slots.footer"><slot name="footer" /></footer>
      </section>
    </div>
  </Teleport>
</template>
