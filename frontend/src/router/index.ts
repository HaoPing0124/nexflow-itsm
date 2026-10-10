import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    // ---- A. Public brand experience ----
    {
      path: '/',
      name: 'brand-home',
      component: () => import('@/views/brand/BrandHome.vue'),
    },

    // ---- B. Employee service portal ----
    {
      path: '/portal',
      component: () => import('@/layouts/PortalLayout.vue'),
      meta: { requiresAuth: true },
      children: [
        { path: '', name: 'portal-home', component: () => import('@/views/portal/PortalHomeView.vue') },
        { path: 'tickets', name: 'portal-tickets', component: () => import('@/views/portal/PortalTicketsView.vue') },
        { path: 'new', name: 'portal-create', component: () => import('@/views/portal/PortalCreateView.vue') },
        { path: 'faq', name: 'portal-faq', component: () => import('@/views/portal/FaqView.vue') },
      ],
    },

    // ---- C. Enterprise operations workspace ----
    {
      path: '/app/login',
      name: 'app-login',
      component: () => import('@/views/app/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/app',
      component: () => import('@/layouts/AppShell.vue'),
      meta: { requiresAuth: true },
      children: [
        { path: '', redirect: '/app/dashboard' },
        {
          path: 'dashboard',
          name: 'app-dashboard',
          component: () => import('@/views/app/DashboardView.vue'),
          meta: { roles: ['ADMIN', 'SUPERVISOR'], permission: 'stats:view' },
        },
        {
          path: 'workbench',
          name: 'app-workbench',
          component: () => import('@/views/app/WorkbenchView.vue'),
        },
        {
          path: 'tickets',
          name: 'app-tickets',
          component: () => import('@/views/app/TicketListView.vue'),
          meta: { permission: 'ticket:view' },
        },
        {
          path: 'tickets/new',
          name: 'app-ticket-create',
          component: () => import('@/views/app/TicketCreateView.vue'),
          meta: { permission: 'ticket:create' },
        },
        {
          path: 'tickets/:id(\\d+)',
          name: 'app-ticket-detail',
          component: () => import('@/views/app/TicketDetailView.vue'),
          meta: { permission: 'ticket:view' },
        },
        {
          path: 'approvals',
          name: 'app-approvals',
          component: () => import('@/views/app/ApprovalCenterView.vue'),
          meta: { permission: 'approval:decide' },
        },
        {
          path: 'rules',
          name: 'app-rules',
          component: () => import('@/views/app/RulesView.vue'),
          meta: { roles: ['ADMIN'], permission: 'config:write' },
        },
        {
          path: 'analytics',
          name: 'app-analytics',
          component: () => import('@/views/app/AnalyticsView.vue'),
          meta: { roles: ['ADMIN', 'SUPERVISOR'], permission: 'stats:view' },
        },
        {
          path: 'audit',
          name: 'app-audit',
          component: () => import('@/views/app/AuditView.vue'),
          meta: { roles: ['ADMIN'], permission: 'audit:view' },
        },
        {
          path: 'config',
          name: 'app-config',
          component: () => import('@/views/app/ConfigView.vue'),
          meta: { roles: ['ADMIN'], permission: 'config:write' },
        },
      ],
    },

    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()

  if (to.meta.public) return true
  if (!to.meta.requiresAuth) return true

  if (!auth.isLoggedIn) {
    return { path: '/app/login', query: { redirect: to.fullPath } }
  }
  if (!auth.user) {
    try {
      await auth.fetchMe()
      auth.$state // ensure state settled
    } catch {
      auth.logout()
      return { path: '/app/login', query: { redirect: to.fullPath } }
    }
  }

  const roles = to.meta.roles as string[] | undefined
  if (roles && !roles.some((r) => auth.hasRole(r))) return { path: '/app/workbench' }

  const permission = to.meta.permission as string | undefined
  if (permission && !auth.hasPermission(permission)) return { path: '/app/workbench' }

  return true
})

export default router
