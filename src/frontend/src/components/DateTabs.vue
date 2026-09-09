<script setup lang="ts">
defineProps<{modelValue:string;options:Array<{label:string;value:string}>}>()
defineEmits<{ 'update:modelValue':[value:string] }>()
function dateText(value:string){return `${value.slice(5,7)}月${value.slice(8,10)}日`}
</script>

<template>
  <nav class="segmented date-tabs" aria-label="预约日期">
    <button v-for="option in options" :key="option.value" type="button" :class="{active:modelValue===option.value}" @click="$emit('update:modelValue',option.value)">
      <b>{{ option.label }}</b><span>{{ dateText(option.value) }}</span>
    </button>
  </nav>
</template>
