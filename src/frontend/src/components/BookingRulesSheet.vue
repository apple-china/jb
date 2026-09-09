<script setup lang="ts">
import AppSheet from './AppSheet.vue'
import type { Role } from '../types'
defineProps<{open:boolean;role:Role}>()
defineEmits<{close:[]}>()
</script>

<template>
  <AppSheet :open="open" title="预约规则" fullscreen @close="$emit('close')">
    <div class="rules-copy">
      <h3>预约时间</h3><p>仅支持今天或明天，按 10 分钟刻度选择，每次占用 20 分钟。</p>
      <template v-if="role==='STREAMER'">
        <h3>提交边界</h3><p>新建、修改和取消均须至少提前 20 分钟，恰好提前 20 分钟允许。</p>
        <h3>修改与取消</h3><p>每个预约日期最多成功修改 3 次、取消 2 次；取消后可以重新预约，已使用次数不会清零。</p>
      </template>
      <template v-else-if="role==='SUPER_ADMIN'||role==='OPERATOR'">
        <h3>管理操作</h3><p>代预约和调整须至少提前 1 分钟；资源与排班必须有效，化妆师时间重叠会明确标记并允许提交。</p>
      </template>
      <template v-else-if="role==='MAKEUP'">
        <h3>化妆师代预约</h3><p>仅可为本人创建安排，须至少提前 1 分钟；时间重叠会明确标记并允许提交。</p>
      </template>
      <template v-else>
        <h3>查看权限</h3><p>观察员可查看预约记录与签到结果，不可添加、修改、取消、发送卡片或导出数据。</p>
      </template>
    </div>
  </AppSheet>
</template>
