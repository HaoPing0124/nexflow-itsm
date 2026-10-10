import { ref, onMounted, onUnmounted, watch, type Ref } from 'vue'

/** 是否应降低动效（系统偏好） */
export function prefersReducedMotion(): boolean {
  if (typeof window === 'undefined' || !window.matchMedia) return false
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

/**
 * 数字滚动：把 from → target 的递增过程渲染出来。
 * 接受 getter，这样目标值变化时（例如从演示数据切到真实数据）会重新计数，
 * 而不是停在首次渲染的值上。尊重 prefers-reduced-motion（直接落到终值）。
 */
export function useCountUp(getTarget: () => number, duration = 900, decimals = 0) {
  const value = ref(0)
  let raf = 0

  function settle(target: number) {
    value.value = decimals > 0 ? Number(target.toFixed(decimals)) : Math.round(target)
  }

  function run(from: number) {
    const target = getTarget()
    if (prefersReducedMotion()) {
      settle(target)
      return
    }
    cancelAnimationFrame(raf)
    const start = performance.now()
    const tick = (now: number) => {
      const p = Math.min(1, (now - start) / duration)
      // easeOutExpo：开头快、结尾稳，数字落定时不抖
      const e = p === 1 ? 1 : 1 - Math.pow(2, -10 * p)
      value.value = decimals > 0 ? Number((from + (target - from) * e).toFixed(decimals)) : Math.round(from + (target - from) * e)
      if (p < 1) raf = requestAnimationFrame(tick)
    }
    raf = requestAnimationFrame(tick)
  }

  onMounted(() => run(0))
  // 目标值变化 → 从当前显示值继续滚向新目标
  watch(getTarget, () => run(value.value))
  onUnmounted(() => cancelAnimationFrame(raf))

  return value
}

/**
 * 滚动揭示：进入视口后才播放入场，避免长页面一次性全播完。
 * 尊重 prefers-reduced-motion（直接视为可见）。
 */
export function useReveal(threshold = 0.12): { el: Ref<HTMLElement | null>; shown: Ref<boolean> } {
  const el = ref<HTMLElement | null>(null)
  const shown = ref(false)
  let io: IntersectionObserver | null = null

  onMounted(() => {
    if (prefersReducedMotion() || !el.value) {
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
      { threshold },
    )
    io.observe(el.value)
  })

  onUnmounted(() => io?.disconnect())

  return { el, shown }
}