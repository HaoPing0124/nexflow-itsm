<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { prefersReducedMotion } from '@/composables/useMotion'

/**
 * 逐字模糊揭示：参考稿的标志性入场——
 * 每个字从 blur(10px) + 下沉 归位，形成一次连贯的显影。
 * 中文无空格，所以按字符切分；尊重 prefers-reduced-motion。
 */
const props = withDefaults(
  defineProps<{ text: string; stagger?: number; delay?: number }>(),
  { stagger: 0.035, delay: 0.05 },
)

const el = ref<HTMLElement | null>(null)
const shown = ref(false)
let io: IntersectionObserver | null = null

onMounted(() => {
  if (prefersReducedMotion() || typeof IntersectionObserver === 'undefined' || !el.value) {
    shown.value = true
    return
  }
  io = new IntersectionObserver(
    (entries) => {
      for (const e of entries) {
        if (e.isIntersecting) {
          shown.value = true
          io?.disconnect()
        }
      }
    },
    { threshold: 0.2 },
  )
  io.observe(el.value)
})
onUnmounted(() => io?.disconnect())
</script>

<template>
  <span ref="el" class="inline-block">
    <span
      v-for="(ch, i) in Array.from(props.text)"
      :key="i"
      class="blur-char inline-block"
      :class="{ 'is-in': shown }"
      :style="{ animationDelay: `${delay + i * stagger}s` }"
      aria-hidden="true"
    >{{ ch === ' ' ? '\u00A0' : ch }}</span>
    <span class="sr-only">{{ text }}</span>
  </span>
</template>
