<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { avatarInitial } from '../utils/display'

const props=defineProps<{src?:string;fallback?:string;name?:string;alt?:string}>()
const primaryFailed=ref(false),fallbackFailed=ref(false),loaded=ref(false)
watch(()=>[props.src,props.fallback],()=>{primaryFailed.value=false;fallbackFailed.value=false;loaded.value=false})
const resolvedSrc=computed(()=>!primaryFailed.value&&props.src?props.src:!fallbackFailed.value?props.fallback:undefined)
function onError(){
  if(resolvedSrc.value===props.src)primaryFailed.value=true
  else fallbackFailed.value=true
  loaded.value=false
}
</script>

<template>
  <span class="avatar-image" :class="{loaded}">
    <img v-if="resolvedSrc" :src="resolvedSrc" :alt="alt ?? ''" @load="loaded=true" @error="onError" />
    <span v-else aria-hidden="true">{{ avatarInitial(name) }}</span>
  </span>
</template>
