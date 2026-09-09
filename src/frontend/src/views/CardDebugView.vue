<script setup lang="ts">
import { onMounted, ref } from 'vue'
import BrandMark from '../components/BrandMark.vue'
import AppToast from '../components/AppToast.vue'
import { useAppToast } from '../composables/useAppToast'
const cards = ref<any[]>([]); const userId = ref('streamer01'); const {toast,showApiError}=useAppToast()
async function load(){ try { const r=await fetch(`/api/v1/mock/cards?userId=${userId.value}`,{credentials:'include'}); const p=await r.json(); if(!r.ok) throw new Error(p?.error?.message); cards.value=p.data } catch(e){showApiError(e,'读取失败。')} }
onMounted(load)
</script>
<template><main class="debug-page"><header class="page-top"><BrandMark compact /><RouterLink to="/admin">返回管理端</RouterLink></header><section class="debug-shell"><div class="section-heading"><div><span class="eyebrow">Local / Test only</span><h1>群卡片 Mock</h1></div><label>私有视角<select v-model="userId" @change="load"><option v-for="id in ['streamer01','streamer02','streamer03','streamer04','admin02','admin01']" :key="id">{{ id }}</option></select></label></div><article v-for="card in cards" :key="card.outTrackId" class="mock-card"><header><strong>{{ card.cardData?.title || '加贝云·今日化妆安排' }}</strong><span>v{{ card.contentVersion }}</span></header><pre>{{ card.cardData?.schedule_markdown || '今天暂时没有预约' }}</pre><div v-if="card.hasPrivateData" class="private-data"><b>我的预约</b><p>{{ card.privateData?.my_appointment }}</p></div><div v-else class="private-data"><b>无 privateData</b><p>该身份不是启用主播；纯管理员不会生成私有区。</p></div><footer>{{ card.outTrackId }} · {{ card.status }}</footer></article></section><AppToast :message="toast.message" :kind="toast.kind"/></main></template>
