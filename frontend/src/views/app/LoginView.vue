<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowRight, Play } from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'
import { useAppShellStore } from '@/stores/appShell'

const auth = useAuthStore()
const appShell = useAppShellStore()
const route = useRoute()
const router = useRouter()

const username = ref('admin')
const password = ref('admin123')
const loading = ref(false)

const demoAccounts = [
  { u: 'admin', p: 'admin123', role: '管理员' },
  { u: 'supervisor', p: 'sup123', role: '主管' },
  { u: 'wangqiang', p: 'wq123', role: '处理人' },
  { u: 'linxiao', p: 'lx123', role: '员工' },
]

// 纯黑编辑感 hero 底图（低照度植物静物），与参考图同源
const heroImage =
  'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=dark%20moody%20botanical%20still%20life%2C%20delicate%20white%20seed%20pods%20and%20dried%20flowers%20against%20pure%20black%20background%2C%20cinematic%20low%20key%20lighting%2C%20fine%20art%20photography%2C%20deep%20shadows%2C%20elegant%2C%20highly%20detailed&image_size=landscape_16_9'

async function submit() {
  await doLogin(username.value, password.value)
}

async function quickLogin(u: string, p: string) {
  username.value = u
  password.value = p
  await doLogin(u, p)
}

async function doLogin(u: string, p: string) {
  if (!u || !p) {
    ElMessage.warning('请输入账号和密码')
    return
  }
  loading.value = true
  try {
    await auth.login(u, p)
    appShell.buildNav(auth.roles, auth.permissions)
    // 按角色选择落地页，避免员工落到无权限的 dashboard 造成重定向循环
    const home = auth.hasPermission('stats:view') ? '/app/dashboard' : '/app/workbench'
    const redirect = (route.query.redirect as string) || home
    await router.push(redirect)
  } catch (e) {
    ElMessage.error((e as Error).message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="relative min-h-screen overflow-hidden bg-black text-primary">
    <!-- hero backdrop: low-key botanical still life, dimmed into the black -->
    <img :src="heroImage" alt="" class="absolute inset-0 h-full w-full object-cover opacity-60" />
    <div class="absolute inset-0 bg-gradient-to-b from-black/50 via-black/75 to-black" />
    <div class="absolute inset-0 bg-[radial-gradient(60%_45%_at_50%_35%,rgba(255,255,255,0.10),transparent_70%)]" />

    <div class="relative z-10 mx-auto flex min-h-screen w-full max-w-6xl flex-col px-6">
      <!-- top bar -->
      <header class="flex items-center justify-between py-7">
        <div class="flex items-center gap-3">
          <span class="grid h-11 w-11 place-items-center rounded-full border border-white/25 font-heading text-xl italic text-white">N</span>
          <span class="font-heading text-2xl italic tracking-wide text-white">
            NEXFLOW<span class="ml-1 align-super font-body text-[0.5em] not-italic tracking-[0.3em] text-white/45">OS</span>
          </span>
        </div>

        <nav class="hidden items-center gap-1 rounded-full border border-white/15 bg-white/5 px-2 py-1.5 backdrop-blur-xl md:flex">
          <span class="rounded-full px-4 py-1.5 font-body text-sm text-white/85">服务台</span>
          <span class="rounded-full px-4 py-1.5 font-body text-sm text-white/45">工单流转</span>
          <span class="rounded-full px-4 py-1.5 font-body text-sm text-white/45">SLA 时效</span>
          <span class="rounded-full px-4 py-1.5 font-body text-sm text-white/45">数据洞察</span>
        </nav>

        <button
          class="rounded-full border border-white/20 bg-white/5 px-5 py-2.5 font-body text-sm font-medium text-white/80 backdrop-blur-xl transition-colors hover:bg-white hover:text-black"
          @click="quickLogin('admin', 'admin123')"
        >
          演示登录
        </button>
      </header>

      <!-- hero + login -->
      <div class="flex flex-1 flex-col items-center justify-center py-10 text-center">
        <span class="mb-7 inline-flex items-center gap-2.5 rounded-full border border-white/20 bg-white/5 px-4 py-1.5 backdrop-blur-xl">
          <span class="rounded-full bg-white px-2 py-0.5 font-body text-[0.7rem] font-bold tracking-wider text-black">NEW</span>
          <span class="font-body text-sm text-white/70">企业服务协作平台 · 让每张工单都有清晰的下一步</span>
        </span>

        <h1 class="display-serif text-[clamp(2.6rem,7vw,5.75rem)] text-white">
          让每一次服务请求<br class="hidden sm:block" />都有清晰的下一步
        </h1>

        <p class="mt-7 max-w-2xl font-body text-lg leading-relaxed text-white/60">
          从提报到审批、分配、处理与 SLA 追踪，把散落在邮件与群聊里的服务请求，
          收拢为一条可度量、可追溯的协作链路。
        </p>

        <!-- login panel -->
        <form
          class="mt-11 w-full max-w-2xl rounded-[26px] border border-white/15 bg-black/55 p-6 text-left backdrop-blur-2xl"
          @submit.prevent="submit"
        >
          <div class="grid items-end gap-4 sm:grid-cols-2">
            <label class="block">
              <span class="mb-2 block font-body text-xs tracking-[0.2em] text-white/50">账号</span>
              <el-input v-model="username" size="large" placeholder="用户名" class="nf-pill-input" />
            </label>
            <label class="block">
              <span class="mb-2 block font-body text-xs tracking-[0.2em] text-white/50">密码</span>
              <el-input
                v-model="password"
                size="large"
                type="password"
                show-password
                placeholder="密码"
                class="nf-pill-input"
                @keyup.enter="submit"
              />
            </label>
          </div>

          <div class="mt-5 flex flex-col items-center gap-4 sm:flex-row sm:justify-between">
            <p class="font-body text-xs text-white/40">登录即代表同意平台服务条款与隐私政策</p>
            <el-button type="primary" size="large" :loading="loading" class="nf-pill-btn w-full sm:w-auto" @click="submit">
              进入工作台
              <ArrowRight :size="17" class="ml-1" />
            </el-button>
          </div>
        </form>

        <!-- demo accounts -->
        <div class="mt-8 flex flex-wrap items-center justify-center gap-2.5">
          <span class="font-body text-xs tracking-[0.2em] text-white/35">演示账号</span>
          <button
            v-for="a in demoAccounts"
            :key="a.u"
            class="inline-flex items-center gap-1.5 rounded-full border border-white/15 px-4 py-2 font-body text-xs text-white/60 backdrop-blur-xl transition-colors hover:border-white/45 hover:text-white"
            @click="quickLogin(a.u, a.p)"
          >
            <Play :size="11" /> {{ a.u }} · {{ a.role }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* Pill-shaped inputs to echo the reference's rounded controls */
.nf-pill-input :deep(.el-input__wrapper) {
  border-radius: 9999px;
  background: rgba(255, 255, 255, 0.06);
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.16) inset;
  padding-left: 18px;
  padding-right: 18px;
}
.nf-pill-input :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.5) inset;
}
.nf-pill-btn {
  border-radius: 9999px;
  padding-left: 26px;
  padding-right: 26px;
}
</style>