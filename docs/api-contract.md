# NEXFLOW API 契约（前后端对齐基准）

## 通用约定

- 基础路径：`/api`
- 认证：`Authorization: Bearer <jwt>`
- 成功响应：`{ "code": 0, "message": "ok", "data": ... }`（`code=0` 成功；非 0 为错误码）
- 错误响应：`{ "code": <errorCode>, "message": "..." , "fieldErrors": {k:v}? }`；HTTP 状态：401 未认证 / 403 无权限（message 含权限名）/ 400 非法流转或参数 / 404 / 409 乐观锁冲突
- 分页：`{ "items": [], "total": n, "page": p, "size": s }`
- 时间：ISO-8601（UTC 存储，前后端展示时转本地 Asia/Shanghai）
- 演示数据：所有演示/种子数据在响应 DTO 里带 `demo: true`（UI 必须显示"演示数据"角标）

## 角色与权限码

角色 code：`REQUESTER`（普通员工）/ `AGENT`（处理人）/ `SUPERVISOR`（主管）/ `ADMIN`。
权限码示例：`ticket:create` `ticket:view` `ticket:assign` `ticket:classify` `ticket:resolve` `ticket:close` `approval:decide` `config:write` `sla:view` `audit:view` `stats:view`。

## 端点表

| 分组 | 方法与路径 | 说明 |
|---|---|---|
| Auth | POST /api/auth/login | body `{username,password}` → `{token, refreshToken, user}` |
| Auth | GET /api/auth/me | → `UserDto`（含 roles, permissions, deptId） |
| 工单 | POST /api/tickets | body `{typeId,title,description,priority,customFields:[{fieldId,value}],attachmentIds:[]}` |
| 工单 | GET /api/tickets | query: `status,typeId,deptId,priority,assigneeId,keyword,page,size,sort,view`（view 预置: `my`/`unassigned`/`at-risk`/`all`）；数据域过滤服务端 |
| 工单 | GET /api/tickets/{id} | → `TicketDetailDto`（含 customFields/comments/attachments/statusHistory/approvals/assignmentTrace/legalActions） |
| 工单 | POST /api/tickets/{id}/classify | body `{note?}` |
| 工单 | POST /api/tickets/{id}/assign | body `{assigneeId,note?}`（手动分配） |
| 工单 | POST /api/tickets/{id}/auto-assign | 触发规则自动分配 |
| 工单 | POST /api/tickets/{id}/start | 开始处理 |
| 工单 | POST /api/tickets/{id}/resolve | body `{resolution}` |
| 工单 | POST /api/tickets/{id}/confirm | 创建人确认关闭 |
| 工单 | POST /api/tickets/{id}/reopen | body `{note?}` |
| 工单 | POST /api/tickets/{id}/cancel | body `{note?}` |
| 工单 | POST /api/tickets/batch | body `{action, ids, assigneeId?}` 批量操作 |
| 评论 | GET /api/tickets/{id}/comments | 内部备注仅同部门/AGENT+ 可见 |
| 评论 | POST /api/tickets/{id}/comments | body `{content, isInternal?}` |
| 附件 | POST /api/files | multipart `file` → `{id,fileName,contentType,size}`；业务内先用 attachmentIds 关联到工单创建 |
| 附件 | GET /api/files/{id}/download | 流式下载，权限：上传者/负责人/同部门/ADMIN |
| 审批 | GET /api/approvals | query `status=PENDING|APPROVED|REJECTED,page,size` |
| 审批 | GET /api/approvals/{id} | 详情（含关联工单摘要） |
| 审批 | POST /api/approvals/{id}/decision | body `{approve:boolean, comment?}` |
| 规则 | GET /api/assignment-rules | 列表（含优先级排序） |
| 规则 | POST/PUT/DELETE /api/assignment-rules[/{id}] | CRUD（ADMIN） |
| 规则 | POST /api/assignment-rules/preview | body `{typeId,deptId?}` → 模拟分配结果与 reason |
| SLA | GET /api/sla/policies | 列表（ADMIN） |
| 通知 | GET /api/notifications/stream | **SSE**：`fetch`+ReadableStream 解析；事件格式 `{type,title,content,link,createdAt}`；心跳注释行 |
| 通知 | GET /api/notifications?unread= | 分页列表 |
| 通知 | POST /api/notifications/{id}/read | 标记已读 |
| 通知 | POST /api/notifications/read-all | 全部已读 |
| 统计 | GET /api/stats/overview | `{total, open, unassigned, atRisk, breached, todayCreated, todayResolved, avgResolveHours, slaRate, demo}` |
| 统计 | GET /api/stats/trend?days=30 | `[{date, created, resolved}]` |
| 统计 | GET /api/stats/sla-risk | 风险单列表（分页） |
| 统计 | GET /api/stats/dept-performance | `[{deptId,deptName,total,resolved,avgHours,slaRate}]` |
| 统计 | GET /api/stats/workload | `[{userId,userName,total,inProgress,exceedLimit}]` |
| 统计 | GET /api/stats/queues | `{pendingApprovals: n, unassigned: n}` |
| 配置 | GET/POST/PUT/DELETE /api/config/users[/{id}] | 用户管理 |
| 配置 | GET/POST/PUT/DELETE /api/config/depts[/{id}] | 部门 |
| 配置 | GET /api/config/roles | 角色+权限树 |
| 配置 | GET/POST/PUT/DELETE /api/config/ticket-types[/{id}] | 工单类型 |
| 配置 | GET/POST /api/config/ticket-types/{id}/fields | 自定义字段定义（type=TEXT/NUMBER/SELECT/DATE/BOOLEAN/USER, optionsJson） |
| 配置 | GET/POST/PUT/DELETE /api/config/sla-policies[/{id}] | SLA 策略 |
| 配置 | GET /api/config/flows | 服务流程/状态机定义（供 WorkflowStepper） |
| 门户 | GET /api/portal/home | `{welcome, templates:[{typeId,name,icon,desc}], faqs:[...], stats:{myPending,myOpen}}` |
| 门户 | GET /api/search?q=&scope= | scope: `ticket|knowledge|user`，→ `[{type,title,link,excerpt}]` |
| 门户 | GET /api/knowledge/faq | 常见问题 |
| AI | POST /api/ai/classify-suggest/{ticketId} | → `{suggestion, confidence, demo:true}`（规则演示，开关 `app.ai.enabled`） |
| AI | POST /api/ai/summarize/{ticketId} | → `{summary, demo:true}` |
| AI | GET /api/ai/faq-recommend?q= | → `[{id,title}]` |
| 审计 | GET /api/audit-logs | query `module,userId,from,to,page,size` |

## 核心 DTO 字段（字段名两端必须一致）

- `TicketListItemDto`: `id, ticketNo, title, typeId, typeName, priority, status, assigneeId, assigneeName, deptId, deptName, creatorId, creatorName, slaDueAt, slaStatus(OK|AT_RISK|BREACHED), createdAt, updatedAt, demo`
- `TicketDetailDto`: 上表 + `description, version(乐观锁), customFields:[{fieldId,fieldName,fieldType,value}], attachments:[{id,fileName,size,contentType}], comments:[{id,authorId,authorName,content,isInternal,createdAt}], statusHistory:[{fromStatus,toStatus,action,operatorName,note,createdAt}], approvals:[{id,status,approverName,comment,requestedAt,decidedAt}], assignmentTrace:[{mode,ruleName,reason,assignedUserName,createdAt}], legalActions:[{action,label}]`
- `UserDto`: `id, username, displayName, email, deptId, deptName, roles:[code], permissions:[code], status`
- 状态码枚举（前后端共享）：`CREATED, CLASSIFIED, PENDING_APPROVAL, ASSIGNED, IN_PROGRESS, RESOLVED, CLOSED, CANCELED`
- 优先级：`LOW, MEDIUM, HIGH, URGENT`
- legalActions 的可能值：`classify, assign, auto_assign, start, resolve, confirm, reopen, cancel`
