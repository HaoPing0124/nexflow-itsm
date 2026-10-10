import { request, downloadUrl } from './http'
import type {
  LoginResult,
  UserDto,
  TicketListItemDto,
  TicketDetailDto,
  TicketCreatePayload,
  TicketListQuery,
  BatchResultDto,
  PageResult,
  TicketTypeDto,
  CustomFieldDefDto,
  ApprovalListItemDto,
  StatsOverviewDto,
  TrendPointDto,
  DeptPerformanceDto,
  WorkloadDto,
  QueueSummaryDto,
  NotificationDto,
  AssignmentRuleDto,
  SlaPolicyDto,
  PortalHomeDto,
  SearchResultDto,
  AuditLogDto,
  DeptDto,
  RoleDto,
  AiSuggestionDto,
  AssignmentPreviewDto,
} from '@/types'

// ---- Auth ----
export const authApi = {
  login: (username: string, password: string) =>
    request<LoginResult>({ method: 'post', url: '/auth/login', data: { username, password } }),
  me: () => request<UserDto>({ method: 'get', url: '/auth/me' }),
}

// ---- Tickets ----
export const ticketApi = {
  list: (params: TicketListQuery) =>
    request<PageResult<TicketListItemDto>>({ method: 'get', url: '/tickets', params }),
  detail: (id: number) => request<TicketDetailDto>({ method: 'get', url: `/tickets/${id}` }),
  create: (payload: TicketCreatePayload) =>
    request<TicketDetailDto>({ method: 'post', url: '/tickets', data: payload }),
  action: (id: number, action: string, body: Record<string, unknown> = {}) =>
    request({ method: 'post', url: `/tickets/${id}/${action}`, data: body }),
  batch: (action: string, ids: number[], assigneeId?: number) =>
    request<BatchResultDto>({ method: 'post', url: '/tickets/batch', data: { action, ids, assigneeId } }),
  comment: (id: number, content: string, isInternal = false) =>
    request({ method: 'post', url: `/tickets/${id}/comments`, data: { content, isInternal } }),
  comments: (id: number) => request({ method: 'get', url: `/tickets/${id}/comments` }),
}

// ---- Files ----
export const fileApi = {
  upload: (file: File) => {
    const form = new FormData()
    form.append('file', file)
    return request<{ id: number; fileName: string; contentType: string; size: number }>({
      method: 'post',
      url: '/files',
      data: form,
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
  downloadUrl: (id: number) => downloadUrl(`/files/${id}/download`),
}

// ---- Approvals ----
export const approvalApi = {
  list: (status: string, page = 1, size = 20) =>
    request<PageResult<ApprovalListItemDto>>({
      method: 'get',
      url: '/approvals',
      params: { status, page, size },
    }),
  decision: (id: number, approve: boolean, comment?: string) =>
    request({ method: 'post', url: `/approvals/${id}/decision`, data: { approve, comment } }),
}

// ---- Stats ----
export const statsApi = {
  overview: () => request<StatsOverviewDto>({ method: 'get', url: '/stats/overview' }),
  trend: (days = 30) =>
    request<TrendPointDto[]>({ method: 'get', url: '/stats/trend', params: { days } }),
  slaRisk: () => request<PageResult<TicketListItemDto>>({ method: 'get', url: '/stats/sla-risk' }),
  deptPerformance: () =>
    request<DeptPerformanceDto[]>({ method: 'get', url: '/stats/dept-performance' }),
  workload: () => request<WorkloadDto[]>({ method: 'get', url: '/stats/workload' }),
  queues: () => request<QueueSummaryDto>({ method: 'get', url: '/stats/queues' }),
}

// ---- Notifications ----
export const notificationApi = {
  list: (unread: boolean) =>
    request<PageResult<NotificationDto>>({ method: 'get', url: '/notifications', params: { unread } }),
  read: (id: number) => request({ method: 'post', url: `/notifications/${id}/read` }),
  readAll: () => request({ method: 'post', url: '/notifications/read-all' }),
}

// ---- Assignment rules ----
export const ruleApi = {
  list: () => request<AssignmentRuleDto[]>({ method: 'get', url: '/assignment-rules' }),
  create: (data: Partial<AssignmentRuleDto>) =>
    request({ method: 'post', url: '/assignment-rules', data }),
  update: (id: number, data: Partial<AssignmentRuleDto>) =>
    request({ method: 'put', url: `/assignment-rules/${id}`, data }),
  remove: (id: number) => request({ method: 'delete', url: `/assignment-rules/${id}` }),
  preview: (typeId: number, deptId?: number) =>
    request<AssignmentPreviewDto>({ method: 'post', url: '/assignment-rules/preview', data: { typeId, deptId } }),
}

// ---- SLA ----
export const slaApi = {
  list: () => request<SlaPolicyDto[]>({ method: 'get', url: '/sla/policies' }),
}

// ---- Portal ----
export const portalApi = {
  home: () => request<PortalHomeDto>({ method: 'get', url: '/portal/home' }),
  search: (q: string, scope = 'all') =>
    request<SearchResultDto[]>({ method: 'get', url: '/search', params: { q, scope } }),
  faq: () => request<{ id: number; question: string; answer: string }[]>({ method: 'get', url: '/knowledge/faq' }),
}

// ---- Config ----
export const configApi = {
  users: () => request<PageResult<UserDto>>({ method: 'get', url: '/config/users', params: { size: 200 } }),
  depts: () => request<DeptDto[]>({ method: 'get', url: '/config/depts' }),
  roles: () => request<RoleDto[]>({ method: 'get', url: '/config/roles' }),
  ticketTypes: () => request<TicketTypeDto[]>({ method: 'get', url: '/config/ticket-types' }),
  fields: (typeId: number) =>
    request<CustomFieldDefDto[]>({ method: 'get', url: `/config/ticket-types/${typeId}/fields` }),
}

// ---- Audit ----
export const auditApi = {
  list: (params: Record<string, unknown>) =>
    request<PageResult<AuditLogDto>>({ method: 'get', url: '/audit-logs', params }),
}

// ---- AI ----
export const aiApi = {
  classifySuggest: (ticketId: number) =>
    request<AiSuggestionDto>({ method: 'post', url: `/ai/classify-suggest/${ticketId}` }),
  summarize: (ticketId: number) =>
    request<{ summary: string; demo: boolean }>({ method: 'post', url: `/ai/summarize/${ticketId}` }),
  faqRecommend: (q: string) =>
    request<{ id: number; title: string }[]>({ method: 'get', url: '/ai/faq-recommend', params: { q } }),
}
