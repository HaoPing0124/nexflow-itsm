<script setup lang="ts">
import { useCountUp } from '@/composables/useMotion'

const props = withDefaults(
  defineProps<{
    label: string
    value: number
    suffix?: string
    prefix?: string
    decimals?: number
    tone?: 'brand' | 'success' | 'warning' | 'danger' | 'neutral'
    /** 领衔卡：提升视觉层级，一屏只应有一个 */
    lead?: boolean
    hint?: string
  }>(),
  { decimals: 0, tone: 'neutral', lead: false },
)

const shown = useCountUp(() => props.value, 900, props.decimals)

const toneClass = {
  brand: 'text-brand',
  success: 'text-success',
  warning: 'text-warning',
  danger: 'text-danger',
  neutral: 'text-primary',
}[props.tone]
</script>

<template>
  <div class="card-hover p-5" :class="lead ? 'surface-lead' : 'surface-card'">
    <p class="kicker">{{ label }}</p>
    <p class="metric mt-3" :class="[toneClass, lead ? 'text-6xl' : 'text-4xl']">
      <span v-if="prefix">{{ prefix }}</span><span class="tnum">{{ shown }}</span><span v-if="suffix" class="text-[0.5em] align-top ml-0.5">{{ suffix }}</span>
    </p>
    <p v-if="hint" class="mt-2 font-body text-xs text-secondary">{{ hint }}</p>
  </div>
</template>