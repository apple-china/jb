<script setup lang="ts">
import { LockKeyhole } from 'lucide-vue-next'
import BrandMark from '../components/BrandMark.vue'
</script>
<template><main class="center-page"><section class="message-card"><BrandMark compact /><div class="status-illustration"><LockKeyhole :size="30" /></div><h1>暂时没有使用权限</h1><p>该账号未启用，请联系管理员开通后再试。</p><RouterLink class="button secondary" to="/login">返回登录</RouterLink></section></main></template>
