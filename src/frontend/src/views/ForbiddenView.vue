<script setup lang="ts">
import { LockKeyhole } from 'lucide-vue-next'
import BrandMark from '../components/BrandMark.vue'
</script>
<template><main class="center-page"><section class="message-card"><BrandMark compact /><div class="status-illustration"><LockKeyhole :size="30" /></div><h1>暂时没有使用权限</h1><p>如需预约，请联系管理员为你开通主播身份。</p><RouterLink class="button secondary" to="/login">返回 Mock 身份选择</RouterLink></section></main></template>
