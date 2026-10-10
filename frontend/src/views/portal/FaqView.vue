<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ChevronDown } from 'lucide-vue-next'
import { withDemoFallback } from '@/api/fallback'
import { portalApi } from '@/api/modules'

interface FaqItem {
  id: number
  question: string
  answer: string
}

const demoFaqs: FaqItem[] = [
  { id: 1, question: '如何申请账号权限？', answer: '在服务门户选择「账号权限申请」类型，填写申请系统与权限范围，提交后由主管审批，审批通过后 IT 自动开通。' },
  { id: 2, question: '设备报修多久响应？', answer: '设备急修类工单首次响应时限为 30 分钟，一般报修 4 小时内响应，具体以对应 SLA 策略为准。' },
  { id: 3, question: '如何查看处理进度？', answer: '在「我的工单」查看状态流转，工单详情页提供完整的处理历史时间线、评论与审批记录。' },
  { id: 4, question: '工单超时了怎么办？', answer: '系统会根据 SLA 自动标记「即将超时/已超时」并向负责人预警，也可在详情页留言催促。' },
]

const faqs = ref<FaqItem[]>(demoFaqs)
const open = ref<number | null>(0)
const demo = ref(false)
const loading = ref(true)

onMounted(async () => {
  const { data, demo: isDemo } = await withDemoFallback(
    () => portalApi.faq(),
    () => demoFaqs,
  )
  faqs.value = data
  demo.value = isDemo
  loading.value = false
})
</script>

<template>
  <div class="space-y-6">
    <div>
      <h1 class="font-heading text-2xl italic">常见问题</h1>
      <p class="mt-1 font-body text-sm text-secondary">服务知识库</p>
    </div>

    <p v-if="demo" class="kicker">演示数据（未连接后端）</p>
    <p v-else-if="loading" class="kicker">加载中…</p>

    <div class="surface-card divide-y divide-[var(--nf-border)] p-2">
      <div v-for="(f, i) in faqs" :key="f.id" class="p-4">
        <button class="flex w-full items-center justify-between text-left" @click="open = open === i ? null : i">
          <span class="font-body font-medium text-primary">{{ f.question }}</span>
          <ChevronDown :size="16" class="text-secondary transition-transform" :class="{ 'rotate-180': open === i }" />
        </button>
        <p v-if="open === i" class="mt-3 font-body text-sm leading-relaxed text-secondary">{{ f.answer }}</p>
      </div>
    </div>
  </div>
</template>
