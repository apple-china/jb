<script setup lang="ts">
defineProps<{value:string}>()
</script>

<template><time class="time-display" :datetime="value"><span>{{ value.slice(0,2) }}</span><i>:</i><span>{{ value.slice(3,5) }}</span></time></template>
