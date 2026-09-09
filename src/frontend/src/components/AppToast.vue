<script setup lang="ts">
import { AlertTriangle, CheckCircle2, Info, XCircle } from 'lucide-vue-next'
import type { ToastKind } from '../composables/useAppToast'

defineProps<{ message:string; kind:ToastKind }>()
const icons={info:Info,success:CheckCircle2,warning:AlertTriangle,error:XCircle}
</script>

<template>
  <Teleport to="body">
    <Transition name="toast">
      <div v-if="message" class="app-toast" :class="`app-toast-${kind}`" :role="kind==='error'||kind==='warning'?'alert':'status'" aria-live="assertive">
        <component :is="icons[kind]" :size="19" />
        <span>{{ message }}</span>
      </div>
    </Transition>
  </Teleport>
</template>
