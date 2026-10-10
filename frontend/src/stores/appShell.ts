import { defineStore } from 'pinia'

export interface NavItem {
  label: string
  path: string
  icon: string
  roles?: string[]
  permission?: string
}

// Role-based dynamic navigation for /app
const ALL_NAV: NavItem[] = [
  { label: '管理概览', path: '/app/dashboard', icon: 'LayoutDashboard', roles: ['ADMIN', 'SUPERVISOR'], permission: 'stats:view' },
  { label: '我的工作台', path: '/app/workbench', icon: 'Briefcase', roles: ['AGENT', 'SUPERVISOR', 'ADMIN'] },
  { label: '工单列表', path: '/app/tickets', icon: 'Ticket', permission: 'ticket:view' },
  { label: '审批中心', path: '/app/approvals', icon: 'BadgeCheck', permission: 'approval:decide' },
  { label: '分配规则', path: '/app/rules', icon: 'GitBranch', roles: ['ADMIN'], permission: 'config:write' },
  { label: '数据分析', path: '/app/analytics', icon: 'BarChart3', roles: ['ADMIN', 'SUPERVISOR'], permission: 'stats:view' },
  { label: '审计日志', path: '/app/audit', icon: 'ScrollText', roles: ['ADMIN'], permission: 'audit:view' },
  { label: '配置中心', path: '/app/config', icon: 'Settings', roles: ['ADMIN'], permission: 'config:write' },
]

export const useAppShellStore = defineStore('appShell', {
  state: () => ({
    collapsed: false,
    nav: [] as NavItem[],
  }),
  actions: {
    buildNav(roles: string[], permissions: string[]) {
      this.nav = ALL_NAV.filter((item) => {
        if (item.roles && !item.roles.some((r) => roles.includes(r))) return false
        if (item.permission && !permissions.includes(item.permission)) return false
        return true
      })
    },
    toggleCollapsed() {
      this.collapsed = !this.collapsed
    },
  },
})
