// NEXFLOW frontend types — aligned with docs/api-contract.md
// ============================================================

// ---- Common ----
export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export interface PageResult<T> {
  items: T[]
  total: number
  page: number
  size: number
}

// ---- Auth / User ----
export interface UserDto {
  id: number
  username: string
  displayName: string
  email: string
  deptId: number | null
  deptName: string | null
  roles: string[]
  permissions: string[]
  status: string
}

export interface LoginResult {
  token: string
  refreshToken: string
  user: UserDto
}

// ---- Ticket ----
export type TicketStatus =
  | 'CREATED'
  | 'CLASSIFIED'
  | 'PENDING_APPROVAL'
  | 'ASSIGNED'
  | 'IN_PROGRESS'
  | 'RESOLVED'
  | 'CLOSED'
  | 'CANCELED'

export type Priority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT'
export type SlaStatus = 'OK' | 'AT_RISK' | 'BREACHED'

export interface TicketListItemDto {
  id: number
  ticketNo: string
  title: string
  typeId: number
  typeName: string
  priority: Priority
  status: TicketStatus
  assigneeId: number | null
  assigneeName: string | null
  deptId: number | null
  deptName: string | null
  creatorId: number
  creatorName: string
  slaDueAt: string | null
  slaStatus: SlaStatus | null
  createdAt: string
  updatedAt: string
  demo?: boolean
}

export interface CustomFieldValueDto {
  fieldId: number
  fieldName: string
  fieldType: string
  value: string | null
}

export interface CommentDto {
  id: number
  authorId: number
  authorName: string
  content: string
  isInternal: boolean
  createdAt: string
}

export interface AttachmentDto {
  id: number
  fileName: string
  size: number
  contentType: string
}

export interface StatusHistoryDto {
  /** 首条历史（创建）没有"前一个状态"，后端返回 null，前端需按空值处理 */
  fromStatus: TicketStatus | null
  toStatus: TicketStatus
  action: string
  operatorName: string
  note: string | null
  createdAt: string
}

export interface ApprovalDto {
  id: number
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  approverName: string
  comment: string | null
  requestedAt: string
  decidedAt: string | null
}

export interface AssignmentTraceDto {
  mode: 'AUTO' | 'MANUAL'
  ruleName: string | null
  reason: string
  assignedUserName: string
  createdAt: string
}

export type LegalAction =
  | 'classify'
  | 'assign'
  | 'auto_assign'
  | 'start'
  | 'resolve'
  | 'confirm'
  | 'reopen'
  | 'cancel'

/** 后端返回的可执行操作：动作码 + 展示文案（文案由服务端给出，前端不再自建映射） */
export interface LegalActionDto {
  action: LegalAction
  label: string
}

export interface TicketDetailDto extends TicketListItemDto {
  description: string
  version: number
  customFields: CustomFieldValueDto[]
  attachments: AttachmentDto[]
  comments: CommentDto[]
  statusHistory: StatusHistoryDto[]
  approvals: ApprovalDto[]
  assignmentTrace: AssignmentTraceDto[]
  legalActions: LegalActionDto[]
}

/** 自动分配候选人（用于"手动分配"时的选择器与可解释说明） */
export interface AssignmentCandidateDto {
  userId: number
  userName: string
  workload: number
  assignedCount: number
  skillTag: string | null
}

export interface AssignmentPreviewDto {
  matched: boolean
  ruleName: string | null
  ruleStrategy: string | null
  candidates: AssignmentCandidateDto[]
  assignedUserId: number | null
  reason: string
}

export interface TicketCreatePayload {
  typeId: number
  title: string
  description: string
  priority: Priority
  customFields: { fieldId: number; value: string | null }[]
  attachmentIds: number[]
}

/** 批量操作结果：后端逐条执行，允许部分成功 */
export interface BatchResultDto {
  success: number[]
  failed: { id: number; message: string }[]
}

export interface TicketListQuery {
  status?: TicketStatus
  typeId?: number
  deptId?: number
  priority?: Priority
  assigneeId?: number
  keyword?: string
  view?: 'my' | 'unassigned' | 'at-risk' | 'all'
  page?: number
  size?: number
  sort?: string
}

// ---- Ticket types / fields ----
export interface TicketTypeDto {
  id: number
  code: string
  name: string
  active: boolean
  requiresApproval: boolean
  autoAssign: boolean
  slaPolicyId: number | null
}

export interface CustomFieldDefDto {
  id: number
  ticketTypeId: number
  fieldCode: string
  fieldName: string
  fieldType: 'TEXT' | 'NUMBER' | 'SELECT' | 'DATE' | 'BOOLEAN' | 'USER'
  required: boolean
  optionsJson: string | null
  sortOrder: number
}

// ---- Approvals ----
export interface ApprovalListItemDto {
  id: number
  ticketId: number
  ticketNo: string
  ticketTitle: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  approverId: number
  approverName: string
  comment: string | null
  requestedAt: string
  decidedAt: string | null
}

// ---- Stats ----
export interface StatsOverviewDto {
  total: number
  open: number
  unassigned: number
  atRisk: number
  breached: number
  todayCreated: number
  todayResolved: number
  avgResolveHours: number
  slaRate: number
  demo: boolean
}

export interface TrendPointDto {
  date: string
  created: number
  resolved: number
}

export interface DeptPerformanceDto {
  deptId: number
  deptName: string
  total: number
  resolved: number
  avgHours: number
  slaRate: number
}

export interface WorkloadDto {
  userId: number
  userName: string
  total: number
  inProgress: number
  exceedLimit: boolean
}

export interface QueueSummaryDto {
  pendingApprovals: number
  unassigned: number
}

// ---- Notifications ----
export interface NotificationDto {
  id: number
  type: string
  title: string
  content: string
  link: string | null
  readFlag: boolean
  createdAt: string
}

export interface SseEventDto {
  type: string
  title: string
  content: string
  link: string | null
  createdAt: string
}

// ---- Assignment rules ----
export type RuleStrategy = 'ROUND_ROBIN' | 'LEAST_WORKLOAD' | 'MOST_SKILLED'

export interface AssignmentRuleDto {
  id: number
  name: string
  active: boolean
  priority: number
  ticketTypeId: number | null
  scopeDeptId: number | null
  strategy: RuleStrategy
  workloadLimit: number
  skillTag: string | null
}

// ---- SLA ----
export interface SlaPolicyDto {
  id: number
  code: string
  name: string
  firstResponseMinutes: number
  resolveMinutes: number
}

// ---- Portal ----
export interface PortalHomeDto {
  welcome: string
  templates: { typeId: number; name: string; icon: string; desc: string }[]
  faqs: { id: number; question: string; answer: string }[]
  stats: { myPending: number; myOpen: number }
}

export interface SearchResultDto {
  type: 'ticket' | 'knowledge' | 'user'
  title: string
  link: string | null
  excerpt: string
}

// ---- Audit ----
// 与后端 AuditLogDto 保持一致：审计记录以单一 entity 字段描述对象（如 "Ticket#12"）
export interface AuditLogDto {
  id: number
  userId: number | null
  username: string | null
  action: string
  module: string
  entity: string | null
  ip: string | null
  createdAt: string
}

// ---- Config ----
export interface DeptDto {
  id: number
  code: string
  name: string
  parentId: number | null
}

export interface RoleDto {
  code: string
  name: string
  permissions: { code: string; name: string }[]
}

// ---- AI ----
export interface AiSuggestionDto {
  suggestion: string
  confidence: number
  demo: boolean
}
