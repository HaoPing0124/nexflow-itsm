<script setup lang="ts">
// 全局路由过渡：让页面切换有方向与节奏，而不是硬跳转
</script>

<template>
  <RouterView v-slot="{ Component, route }">
    <Transition name="nf-page" mode="out-in">
      <component :is="Component" :key="route.path" />
    </Transition>
  </RouterView>
</template>