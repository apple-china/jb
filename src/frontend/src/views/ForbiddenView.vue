<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { LockKeyhole } from 'lucide-vue-next'
import BrandMark from '../components/BrandMark.vue'
const route=useRoute()
const copy=computed(()=>({
  SYSTEM_DISABLED:{title:'访问受限',message:'系统暂未开放'},
  ACCOUNT_DISABLED:{title:'访问受限',message:'暂无访问权限'},
  ACCOUNT_UNREGISTERED:{title:'访问受限',message:'暂未注册'},
  FORBIDDEN:{title:'访问受限',message:'当前账号无法访问此页面'},
} as Record<string,{title:string;message:string}>)[String(route.query.reason??'FORBIDDEN')]??{title:'访问受限',message:'当前账号无法访问此页面'})
</script>
<template><main class="center-page"><section class="message-card"><BrandMark compact /><div class="status-illustration"><LockKeyhole :size="30" /></div><h1>{{ copy.title }}</h1><p>{{ copy.message }}</p><RouterLink class="button secondary" to="/login">返回登录</RouterLink></section></main></template>
