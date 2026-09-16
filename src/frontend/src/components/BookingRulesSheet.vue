<script setup lang="ts">
import AppSheet from './AppSheet.vue'
import type { Role } from '../types'
defineProps<{open:boolean;role:Role}>()
defineEmits<{close:[]}>()
</script>

<template>
  <AppSheet :open="open" title="预约规则" @close="$emit('close')">
    <div class="rules-grid">
      <article><b>预约时间</b><p>仅可选择今天或明天，按 10 分钟刻度预约，每次占用 20 分钟。</p></article>
      <article v-if="role==='STREAMER'||role==='OBSERVER'"><b>主播操作</b><p>预约、修改和取消均需至少提前 20 分钟；每天最多修改 3 次、取消 2 次。</p></article>
      <article v-if="role==='STREAMER'||role==='OBSERVER'"><b>化妆师与签到</b><p>仅显示当日可预约的化妆师。状态依次为待签到、已签到、未到或迟到。</p></article>
      <article v-if="role==='OBSERVER'"><b>查看权限</b><p>观察员可查看预约、签到与统计数据，不可提交或变更预约。</p></article>
      <template v-if="role==='SUPER_ADMIN'||role==='OPERATOR'">
        <article><b>主播预约</b><p>主播须遵守 20 分钟提前量，以及每日修改 3 次、取消 2 次的限制。</p></article>
        <article><b>代预约</b><p>仅可选择启用、在岗且有排班的人员；同日已有有效预约的主播不可重复选择。</p></article>
        <article><b>冲突处理</b><p>管理操作至少提前 1 分钟。重叠时段会明确提示，二次确认后方可提交。</p></article>
      </template>
      <template v-if="role==='MAKEUP'">
        <article><b>主播预约</b><p>主播预约须至少提前 20 分钟，并遵守修改和取消次数限制。</p></article>
        <article><b>化妆师代预约</b><p>仅能使用本人资源代预约；人员、团队与时段须启用并符合当日排班。</p></article>
        <article><b>冲突处理</b><p>操作至少提前 1 分钟。重叠时段需查看提示并二次确认。</p></article>
      </template>
    </div>
  </AppSheet>
</template>
