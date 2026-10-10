<script setup lang="ts">
// Glass container; variant subtle (default) or strong
defineProps<{
  variant?: 'subtle' | 'strong'
  hairline?: boolean
}>()
</script>

<template>
  <div :class="['glass-subtle', variant === 'strong' && 'glass-strong', hairline && 'hairline-border']">
    <slot />
  </div>
</template>
