// NEXFLOW 前端演示数据（演示模式用，均标注 demo:true）
// 仅用于"前端离线流畅演示"，正式联调时由真实 API 替代。
import type {
  UserDto,
  TicketListItemDto,
  TicketDetailDto,
  TicketTypeDto,
  ApprovalListItemDto,
  StatsOverviewDto,
  TrendPointDto,
  DeptPerformanceDto,
  WorkloadDto,
  QueueSummaryDto,
  NotificationDto,
  AssignmentRuleDto,
  SlaPolicyDto,
  DeptDto,
  RoleDto,
  AuditLogDto,
  LegalAction,
} from '@/types'

// ---------------- 账号 ----------------
export const demoUsers: Record<string, { password: string; user: UserDto }> = {
  admin: {
    password: 'admin123',
    user: {
      id: 1, username: 'admin', displayName: '系统管理员', email: 'admin@nexflow.io', deptId: null, deptName: '平台中心',
      roles: ['ADMIN'],
      permissions: ['ticket:create', 'ticket:view', 'ticket:assign', 'ticket:classify', 'ticket:resolve', 'ticket:close', 'approval:decide', 'config:write', 'sla:view', 'audit:view', 'stats:view'],
      status: 'ACTIVE',
    },
  },
  supervisor: {
    password: 'sup123',
    user: {
      id: 2, username: 'supervisor', displayName: '陈晨', email: 'chenchen@nexflow.io', deptId: 1, deptName: 'IT 部',
      roles: ['SUPERVISOR'],
      permissions: ['ticket:create', 'ticket:view', 'ticket:assign', 'ticket:classify', 'ticket:resolve', 'ticket:close', 'approval:decide', 'sla:view', 'stats:view'],
      status: 'ACTIVE',
    },
  },
  wangqiang: {
    password: 'wq123',
    user: {
      id: 3, username: 'wangqiang', displayName: '王强', email: 'wangqiang@nexflow.io', deptId: 1, deptName: 'IT 部',
      roles: ['AGENT'],
      permissions: ['ticket:view', 'ticket:resolve', 'ticket:classify'],
      status: 'ACTIVE',
    },
  },
  linxiao: {
    password: 'lx123',
    user: {
      id: 7, username: 'linxiao', displayName: '林晓', email: 'linxiao@nexflow.io', deptId: 3, deptName: '人事部',
      roles: ['REQUESTER'],
      permissions: ['ticket:create', 'ticket:view'],
      status: 'ACTIVE',
    },
  },
}

// ---------------- 工单列表 ----------------
const T = (
  id: number, ticketNo: string, title: string, typeId: number, typeName: string,
  priority: TicketListItemDto['priority'], status: TicketListItemDto['status'],
  assigneeId: number | null, assigneeName: string | null, deptId: number, deptName: string,
  creatorId: number, creatorName: string, slaStatus: TicketListItemDto['slaStatus'], daysAgo: number,
): TicketListItemDto => {
  const d = new Date()
  d.setDate(d.getDate() - daysAgo)
  const due = new Date(d)
  due.setHours(due.getHours() + 8)
  return {
    id, ticketNo, title, typeId, typeName, priority, status, assigneeId, assigneeName,
    deptId, deptName, creatorId, creatorName, slaDueAt: due.toISOString(), slaStatus,
    createdAt: d.toISOString(), updatedAt: new Date(d.getTime() + 3600_000).toISOString(), demo: true,
  }
}

export const demoTickets: TicketListItemDto[] = [
  T(1001, 'NF-20261009-0001', '新员工入职需要开通公司邮箱与 OA 账号', 5, '账号权限申请', 'URGENT', 'PENDING_APPROVAL', null, null, 1, 'IT 部', 7, '林晓', 'AT_RISK', 0),
  T(1002, 'NF-20261009-0002', '会议室投影仪无法连接笔记本', 1, 'IT 技术支持', 'HIGH', 'IN_PROGRESS', 3, '王强', 1, 'IT 部', 8, '王芳', 'OK', 1),
  T(1003, 'NF-20261008-0003', '办公耗材（打印纸/墨盒）采购补充', 2, '行政服务', 'MEDIUM', 'ASSIGNED', 4, '李娜', 2, '行政部', 7, '林晓', 'OK', 1),
  T(1004, 'NF-20261008-0004', '员工宿舍空调故障报修', 4, '设备维护', 'HIGH', 'RESOLVED', 6, '赵磊', 4, '设备组', 8, '王芳', 'OK', 2),
  T(1005, 'NF-20261007-0005', '请假流程咨询与年假余额确认', 3, '人事服务', 'LOW', 'CLOSED', 5, '张婷', 3, '人事部', 7, '林晓', 'OK', 3),
  T(1006, 'NF-20261007-0006', '打印机驱动安装失败求助', 1, 'IT 技术支持', 'MEDIUM', 'CLASSIFIED', null, null, 1, 'IT 部', 8, '王芳', 'AT_RISK', 2),
  T(1007, 'NF-20261006-0007', '申请 VM 服务器资源用于测试环境', 1, 'IT 技术支持', 'HIGH', 'IN_PROGRESS', 3, '王强', 1, 'IT 部', 7, '林晓', 'BREACHED', 4),
  T(1008, 'NF-20261005-0008', '办公区域门禁卡权限调整', 5, '账号权限申请', 'MEDIUM', 'CLOSED', 4, '李娜', 2, '行政部', 8, '王芳', 'OK', 5),
  T(1009, 'NF-20261004-0009', '研发部显示器坏点报废申请', 4, '设备维护', 'LOW', 'CLOSED', 6, '赵磊', 4, '设备组', 7, '林晓', 'OK', 6),
  T(1010, 'NF-20261003-0010', '新员工入职手续办理进度咨询', 3, '人事服务', 'LOW', 'CLOSED', 5, '张婷', 3, '人事部', 8, '王芳', 'OK', 7),
]

// ---------------- 工单详情 ----------------
export function getDemoTicketDetail(id: number): TicketDetailDto {
  const base = demoTickets.find((t) => t.id === id) ?? demoTickets[0]
  const d = new Date()
  d.setDate(d.getDate() - 3)
  return {
    ...base,
    description: '详细描述：' + base.title + '。这是演示工单的正文描述，用于展示工单详情的排版与信息层级。',
    version: 3,
    customFields: [
      { fieldId: 1, fieldName: '申请系统', fieldType: 'SELECT', value: 'OA 系统' },
      { fieldId: 2, fieldName: '权限范围', fieldType: 'TEXT', value: '只读 + 工单模块编辑' },
      { fieldId: 3, fieldName: '期望完成日', fieldType: 'DATE', value: '2026-10-12' },
      { fieldId: 4, fieldName: '紧急程度', fieldType: 'SELECT', value: '高' },
    ],
    attachments: [
      { id: 1, fileName: '申请说明.pdf', size: 284_120, contentType: 'application/pdf' },
      { id: 2, fileName: '需求截图.png', size: 96_440, contentType: 'image/png' },
    ],
    comments: [
      { id: 1, authorId: 7, authorName: '林晓', content: '麻烦尽快处理，员工周一入职。', isInternal: false, createdAt: d.toISOString() },
      { id: 2, authorId: 3, authorName: '王强', content: '（内部备注）已确认 OA 管理员权限，待审批通过后即可开通。', isInternal: true, createdAt: new Date(d.getTime() + 7200_000).toISOString() },
    ],
    statusHistory: [
      { fromStatus: null, toStatus: 'CREATED', action: 'create', operatorName: '林晓', note: '提交工单', createdAt: d.toISOString() },
      { fromStatus: 'CREATED', toStatus: 'CLASSIFIED', action: 'classify', operatorName: '王强', note: '已归类为账号权限申请', createdAt: new Date(d.getTime() + 1800_000).toISOString() },
      { fromStatus: 'CLASSIFIED', toStatus: 'PENDING_APPROVAL', action: 'approve-flow', operatorName: '系统', note: '该类型需要主管审批，已发起审批', createdAt: new Date(d.getTime() + 3600_000).toISOString() },
    ],
    approvals: [
      { id: 1, status: 'PENDING', approverName: '陈晨', comment: null, requestedAt: new Date(d.getTime() + 3600_000).toISOString(), decidedAt: null },
    ],
    assignmentTrace: base.assigneeName
      ? [
          {
            mode: 'AUTO',
            ruleName: 'IT 支持轮转',
            reason: `命中 类型=${base.typeName}，策略=最少负载；候选 3 人（王强 2 件 / 李娜 3 件 / 赵磊 5 件），选中 ${base.assigneeName}（当前负载最少）`,
            assignedUserName: base.assigneeName,
            createdAt: new Date(d.getTime() + 5400_000).toISOString(),
          },
        ]
      : [],
    legalActions: legalActionsFor(base.status),
  }
}

/**
 * 演示数据下的可执行操作，刻意与后端 WorkflowEngine.legalActions 的状态机保持一致，
 * 避免"演示能点、真机不能点"的假象。文案也由服务端语义给出。
 */
function legalActionsFor(status: TicketListItemDto['status']): { action: LegalAction; label: string }[] {
  const A = (action: LegalAction, label: string) => ({ action, label })
  switch (status) {
    case 'CREATED':
      return [A('classify', '分类'), A('cancel', '取消工单')]
    case 'CLASSIFIED':
      return [A('assign', '分配'), A('auto_assign', '自动分配'), A('cancel', '取消工单')]
    case 'PENDING_APPROVAL':
      return [A('cancel', '取消工单')]
    case 'ASSIGNED':
      return [A('start', '开始处理')]
    case 'IN_PROGRESS':
      return [A('resolve', '提交解决')]
    case 'RESOLVED':
      return [A('confirm', '确认关闭'), A('reopen', '重新打开')]
    case 'CLOSED':
      return [A('reopen', '重新打开')]
    default:
      return []
  }
}

/** 演示模式下的自动分配候选人（与后端 Candidate 结构一致） */
export function demoAssignmentPreview(typeId: number, typeName: string) {
  const candidates = demoWorkload.map((w) => ({
    userId: w.userId,
    userName: w.userName,
    workload: w.inProgress,
    assignedCount: w.total,
    skillTag: typeName,
  }))
  const best = candidates.slice().sort((a, b) => a.workload - b.workload)[0]
  return {
    matched: true,
    ruleName: 'IT 支持轮转',
    ruleStrategy: 'LEAST_WORKLOAD',
    candidates,
    assignedUserId: best?.userId ?? null,
    reason: `命中 类型=${typeName}，策略=最少负载；候选 ${candidates.length} 人，选中 ${best?.userName ?? '—'}（当前负载最少）`,
    typeId,
  }
}

// ---------------- 工单类型 / 字段 ----------------
export const demoTicketTypes: TicketTypeDto[] = [
  { id: 1, code: 'IT_SUPPORT', name: 'IT 技术支持', active: true, requiresApproval: false, autoAssign: true, slaPolicyId: 1 },
  { id: 2, code: 'ADMIN_SERVICE', name: '行政服务', active: true, requiresApproval: false, autoAssign: true, slaPolicyId: 2 },
  { id: 3, code: 'HR_SERVICE', name: '人事服务', active: true, requiresApproval: false, autoAssign: true, slaPolicyId: 3 },
  { id: 4, code: 'EQUIPMENT_MAINTENANCE', name: '设备维护', active: true, requiresApproval: false, autoAssign: true, slaPolicyId: 4 },
  { id: 5, code: 'ACCOUNT_PERMISSION', name: '账号权限申请', active: true, requiresApproval: true, autoAssign: false, slaPolicyId: 5 },
]

// ---------------- 审批 ----------------
export const demoApprovals: ApprovalListItemDto[] = [
  { id: 1, ticketId: 1001, ticketNo: 'NF-20261009-0001', ticketTitle: '新员工入职需要开通公司邮箱与 OA 账号', status: 'PENDING', approverId: 2, approverName: '陈晨', comment: null, requestedAt: new Date().toISOString(), decidedAt: null },
  { id: 2, ticketId: 1011, ticketNo: 'NF-20261008-0011', ticketTitle: '申请生产环境数据库读权限', status: 'PENDING', approverId: 2, approverName: '陈晨', comment: null, requestedAt: new Date(Date.now() - 86400_000).toISOString(), decidedAt: null },
  { id: 3, ticketId: 1008, ticketNo: 'NF-20261005-0008', ticketTitle: '办公区域门禁卡权限调整', status: 'APPROVED', approverId: 2, approverName: '陈晨', comment: '同意，按申请范围开通', requestedAt: new Date(Date.now() - 4 * 86400_000).toISOString(), decidedAt: new Date(Date.now() - 3 * 86400_000).toISOString() },
]

// ---------------- 统计 ----------------
// 后端契约：slaRate 是 0..1 的小数（见 StatsService），演示数据必须同口径，
// 否则同一块组件在"真实/演示"两种数据下会差 100 倍。
export const demoStatsOverview: StatsOverviewDto = {
  total: 286, open: 53, unassigned: 7, atRisk: 9, breached: 3, todayCreated: 14, todayResolved: 11,
  avgResolveHours: 6.4, slaRate: 0.926, demo: true,
}

function trendFor(daysAgo: number, created: number, resolved: number): TrendPointDto {
  const d = new Date()
  d.setDate(d.getDate() - daysAgo)
  return { date: d.toISOString().slice(0, 10), created, resolved }
}

export const demoTrend: TrendPointDto[] = [
  trendFor(6, 8, 6), trendFor(5, 12, 9), trendFor(4, 10, 11), trendFor(3, 15, 13),
  trendFor(2, 9, 10), trendFor(1, 16, 14), trendFor(0, 14, 11),
]

export const demoDeptPerformance: DeptPerformanceDto[] = [
  { deptId: 1, deptName: 'IT 部', total: 96, resolved: 82, avgHours: 5.2, slaRate: 0.915 },
  { deptId: 2, deptName: '行政部', total: 64, resolved: 58, avgHours: 4.1, slaRate: 0.953 },
  { deptId: 3, deptName: '人事部', total: 52, resolved: 47, avgHours: 6.8, slaRate: 0.892 },
  { deptId: 4, deptName: '设备组', total: 74, resolved: 66, avgHours: 7.5, slaRate: 0.881 },
]

export const demoWorkload: WorkloadDto[] = [
  { userId: 3, userName: '王强', total: 8, inProgress: 4, exceedLimit: false },
  { userId: 4, userName: '李娜', total: 5, inProgress: 3, exceedLimit: false },
  { userId: 5, userName: '张婷', total: 6, inProgress: 2, exceedLimit: false },
  { userId: 6, userName: '赵磊', total: 9, inProgress: 7, exceedLimit: true },
]

export const demoQueues: QueueSummaryDto = { pendingApprovals: 2, unassigned: 7 }

// ---------------- 通知 ----------------
export const demoNotifications: NotificationDto[] = [
  { id: 1, type: 'assign', title: '新工单已分配给你', content: '工单 NF-20261009-0002 已分配给你处理', link: '/app/tickets/1002', readFlag: false, createdAt: new Date().toISOString() },
  { id: 2, type: 'approval', title: '审批结果提醒', content: '你的账号权限申请已通过审批', link: '/app/tickets/1001', readFlag: false, createdAt: new Date(Date.now() - 3600_000).toISOString() },
  { id: 3, type: 'sla', title: 'SLA 即将超时', content: '工单 NF-20261006-0007 即将超时，请尽快处理', link: '/app/tickets/1007', readFlag: true, createdAt: new Date(Date.now() - 7200_000).toISOString() },
]

// ---------------- 规则 / SLA / 部门 / 角色 / 审计 ----------------
export const demoRules: AssignmentRuleDto[] = [
  { id: 1, name: 'IT 支持轮转', active: true, priority: 10, ticketTypeId: 1, scopeDeptId: 1, strategy: 'ROUND_ROBIN', workloadLimit: 5, skillTag: 'it-support' },
  { id: 2, name: '行政最少负载', active: true, priority: 20, ticketTypeId: 2, scopeDeptId: 2, strategy: 'LEAST_WORKLOAD', workloadLimit: 5, skillTag: null },
  { id: 3, name: '设备技能匹配', active: true, priority: 30, ticketTypeId: 4, scopeDeptId: 4, strategy: 'MOST_SKILLED', workloadLimit: 4, skillTag: 'equipment' },
]

export const demoSlaPolicies: SlaPolicyDto[] = [
  { id: 1, code: 'IT_STANDARD', name: 'IT 标准', firstResponseMinutes: 30, resolveMinutes: 480 },
  { id: 2, code: 'ADMIN_STANDARD', name: '行政标准', firstResponseMinutes: 60, resolveMinutes: 1440 },
  { id: 3, code: 'HR_STANDARD', name: '人事标准', firstResponseMinutes: 120, resolveMinutes: 2880 },
  { id: 4, code: 'EQUIP_URGENT', name: '设备急修', firstResponseMinutes: 30, resolveMinutes: 240 },
  { id: 5, code: 'ACCOUNT_APPROVE', name: '账号审批', firstResponseMinutes: 60, resolveMinutes: 1440 },
]

export const demoDepts: DeptDto[] = [
  { id: 1, code: 'IT', name: 'IT 部', parentId: null },
  { id: 2, code: 'ADMIN', name: '行政部', parentId: null },
  { id: 3, code: 'HR', name: '人事部', parentId: null },
  { id: 4, code: 'EQUIPMENT', name: '设备组', parentId: null },
]

export const demoRoles: RoleDto[] = [
  { code: 'REQUESTER', name: '普通员工', permissions: [{ code: 'ticket:create', name: '创建工单' }, { code: 'ticket:view', name: '查看自己工单' }] },
  { code: 'AGENT', name: '处理人', permissions: [{ code: 'ticket:view', name: '查看工单' }, { code: 'ticket:resolve', name: '处理工单' }] },
  { code: 'SUPERVISOR', name: '主管', permissions: [{ code: 'approval:decide', name: '审批' }, { code: 'ticket:assign', name: '分配' }] },
  { code: 'ADMIN', name: '管理员', permissions: [{ code: 'config:write', name: '系统配置' }, { code: 'audit:view', name: '审计' }] },
]

export const demoAuditLogs: AuditLogDto[] = [
  { id: 1, userId: 1, username: 'admin', action: '创建工单类型', module: 'config', entity: 'TicketType#5', ip: '10.0.0.12', createdAt: new Date(Date.now() - 3600_000).toISOString() },
  { id: 2, userId: 2, username: 'supervisor', action: '审批通过', module: 'approval', entity: 'Approval#3', ip: '10.0.0.8', createdAt: new Date(Date.now() - 7200_000).toISOString() },
  { id: 3, userId: 3, username: 'wangqiang', action: '处理工单', module: 'ticket', entity: 'Ticket#1002', ip: '10.0.0.5', createdAt: new Date(Date.now() - 10800_000).toISOString() },
  { id: 4, userId: 7, username: 'linxiao', action: '提交工单', module: 'ticket', entity: 'Ticket#1001', ip: '10.0.0.21', createdAt: new Date(Date.now() - 14400_000).toISOString() },
]