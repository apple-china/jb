<script setup lang="ts">
defineProps<{ compact?: boolean }>()
</script>
<template>
  <div class="brand" :class="{ compact }">
    <img src="/brand/jiabei-logo.png" alt="加贝云" />
    <div><strong>加贝云</strong><span>化妆预约</span></div>
  </div>
</template>
