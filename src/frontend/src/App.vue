<script setup lang="ts">
import BrandMark from './components/BrandMark.vue'
import AppToast from './components/AppToast.vue'
import { authUi } from './auth'
</script>

<template>
  <RouterView />
  <div v-if="authUi.checking" class="auth-overlay" role="status" aria-live="polite">
    <div class="auth-progress"><BrandMark compact /><span class="auth-spinner" /><p>正在确认登录状态…</p></div>
  </div>
  <AppToast :message="authUi.message" :kind="authUi.kind" />
</template>
