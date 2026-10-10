<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts/core'
import { LineChart, BarChart, PieChart } from 'echarts/charts'
import {
  GridComponent,
  TooltipComponent,
  LegendComponent,
  MarkLineComponent,
  MarkPointComponent,
  TitleComponent,
  DatasetComponent,
  GraphicComponent,
} from 'echarts/components'
import { LabelLayout, UniversalTransition } from 'echarts/features'
import { CanvasRenderer } from 'echarts/renderers'
import type { EChartsCoreOption } from 'echarts/core'
import { prefersReducedMotion } from '@/composables/useMotion'

echarts.use([
  LineChart,
  BarChart,
  PieChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  MarkLineComponent,
  MarkPointComponent,
  TitleComponent,
  DatasetComponent,
  GraphicComponent,
  LabelLayout,
  UniversalTransition,
  CanvasRenderer,
])

const props = defineProps<{ option: EChartsCoreOption; height?: string }>()
const el = ref<HTMLDivElement>()
let chart: echarts.ECharts | null = null
let ro: ResizeObserver | null = null
let io: IntersectionObserver | null = null

// 图表在进入视口后才渲染并播放"描画式"生长动画：避免离屏计算，也让动效真正被看见
function render(animate = true) {
  if (!el.value || chart) return
  chart = echarts.init(el.value)
  chart.setOption(
    {
      animation: animate && !prefersReducedMotion(),
      // 参考稿的 animate-draw 手感：长一点、顺着 ease-out 落定、按数据点错峰起笔
      animationDuration: 1600,
      animationDurationUpdate: 700,
      animationEasing: 'cubicOut',
      animationEasingUpdate: 'cubicOut',
      animationDelay: (idx: number) => idx * 40,
      ...props.option,
    },
    true,
  )
  ro = new ResizeObserver(() => chart?.resize())
  ro.observe(el.value)
}

onMounted(() => {
  if (!el.value) return
  if (typeof IntersectionObserver === 'undefined') {
    render(false)
    return
  }
  io = new IntersectionObserver(
    (entries) => {
      for (const e of entries) {
        if (e.isIntersecting) {
          render(true)
          io?.disconnect()
          io = null
        }
      }
    },
    { threshold: 0.15 },
  )
  io.observe(el.value)
})

watch(
  () => props.option,
  (opt) => chart?.setOption(opt, true),
  { deep: true },
)

onBeforeUnmount(() => {
  io?.disconnect()
  ro?.disconnect()
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div ref="el" :style="{ height: height ?? '280px', width: '100%' }" />
</template>