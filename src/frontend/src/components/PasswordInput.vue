<script setup lang="ts">
import { ref } from 'vue'
import { Eye, EyeOff } from 'lucide-vue-next'

defineProps<{
  modelValue: string
  autocomplete: string
  placeholder: string
  maxlength?: number
  name?: string
}>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const visible = ref(false)
</script>

<template>
  <div class="password-field">
    <input
      :value="modelValue"
      :type="visible ? 'text' : 'password'"
      :autocomplete="autocomplete"
      :placeholder="placeholder"
      :maxlength="maxlength"
      :name="name ?? 'password'"
      data-lpignore="true"
      data-1p-ignore
      data-bwignore="true"
      autocapitalize="off"
      :spellcheck="false"
      @input="emit('update:modelValue', ($event.target as HTMLInputElement).value)"
    />
    <button
      type="button"
      class="password-visibility"
      :aria-label="visible ? '隐藏密码' : '显示密码'"
      :aria-pressed="visible"
      @click="visible = !visible"
    >
      <EyeOff v-if="visible" :size="19" />
      <Eye v-else :size="19" />
    </button>
  </div>
</template>
