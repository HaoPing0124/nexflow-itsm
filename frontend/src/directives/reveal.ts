import type { Directive, DirectiveBinding } from 'vue'
import { prefersReducedMotion } from '@/composables/useMotion'

/**
 * v-reveal — 内容随滚动渐进显示。
 *
 * 元素进入视口后才播放入场（透明度 + 位移 + 轻微缩放），播完即卸载样式，
 * 因此不会残留 transform 而影响 hover 抬升等交互。
 *
 * 用法：
 *   v-reveal                无延迟
 *   v-reveal="i * 70"       毫秒延迟，用于列表 stagger
 *   v-reveal="{ delay: 80, y: 30 }"
 */
interface RevealOptions {
  delay?: number
  y?: number
}

function normalize(value: unknown): RevealOptions {
  if (typeof value === 'number') return { delay: value }
  if (value && typeof value === 'object') return value as RevealOptions
  return {}
}

// 单一共享 observer，避免为每个元素各建一个
let observer: IntersectionObserver | null = null

function getObserver(): IntersectionObserver {
  if (!observer) {
    observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (!entry.isIntersecting) continue
          const el = entry.target as HTMLElement
          el.classList.add('reveal-play')
          observer?.unobserve(el)
        }
      },
      { threshold: 0.08, rootMargin: '0px 0px -6% 0px' },
    )
  }
  return observer
}

const reveal: Directive<HTMLElement, unknown> = {
  mounted(el: HTMLElement, binding: DirectiveBinding) {
    if (prefersReducedMotion()) return
    const { delay = 0, y } = normalize(binding.value)
    el.classList.add('reveal')
    if (delay) el.style.setProperty('--reveal-delay', `${delay}ms`)
    if (typeof y === 'number') el.style.setProperty('--reveal-y', `${y}px`)
    el.addEventListener(
      'animationend',
      () => {
        el.classList.remove('reveal', 'reveal-play')
        el.style.removeProperty('--reveal-delay')
        el.style.removeProperty('--reveal-y')
      },
      { once: true },
    )
    getObserver().observe(el)
  },
  unmounted(el: HTMLElement) {
    observer?.unobserve(el)
  },
}

export default reveal