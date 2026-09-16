<script setup lang="ts">
import { onBeforeUnmount, watch } from 'vue'
import { X } from 'lucide-vue-next'
const props=defineProps<{ open: boolean; title: string; subtitle?: string; fullscreen?: boolean }>()
defineEmits<{ close: [] }>()
let previousOverflow=''
watch(()=>props.open,open=>{if(open){previousOverflow=document.body.style.overflow;document.body.style.overflow='hidden'}else document.body.style.overflow=previousOverflow},{immediate:true})
onBeforeUnmount(()=>{document.body.style.overflow=previousOverflow})
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
