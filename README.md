# NEXFLOW — 企业智能服务协作平台

「让每一项服务请求，都有清晰的下一步。」

Enterprise Intelligent Service & Workflow Platform：员工请求 → 智能分类 → 审批/分配 → 处理 → 确认 → 关闭，全流程透明可追踪。

## 技术栈

| 层 | 技术 |
|---|---|
| 前端 | Vue 3 · TypeScript · Vite · Vue Router · Pinia · Tailwind CSS · Element Plus · ECharts · lucide |
| 后端 | Spring Boot 3.5 · Java 25 · Spring Security(JWT) · Spring Data JPA · Validation |
| 数据库 | H2(MySQL 兼容模式, 本地开发) · MySQL 8(生产 / docker-compose) |
| 部署 | Docker Compose（MySQL 8 + backend） |

## 三个体验空间

- `/` — 品牌官网：产品能力、工作流程、应用场景、数据洞察、企业安全
- `/portal` — 员工自助门户：服务目录、快速请求、我的工单、FAQ
- `/app` — 企业运营工作台：看板 / 工单列表 / 详情 / 创建 / 审批中心 / 分配规则 / 数据分析 / 审计 / 配置中心（角色动态导航）

## 本地启动

### 后端（端口 8080）

**在 IntelliJ IDEA 中运行（推荐）**

1. `File → Open`，选择 `backend` 目录（即含 `pom.xml` 的那一层），以 Maven 项目导入，等待依赖下载完成。
2. 确认 `File → Project Structure → Project SDK` 为 **JDK 25**（本项目 `pom.xml` 的 `java.version` 为 25）。
3. 确认已开启注解处理：`Settings → Build → Compiler → Annotation Processors → Enable annotation processing`（Lombok 依赖此项，IDEA 通常会在导入时提示）。
4. 运行 `src/main/java/com/nexflow/NexflowApplication.java` 的 `main` 方法。
5. 以 `local` profile 启动：`Run → Edit Configurations → Active profiles` 填 `local`；或在 `application.yml` 中已默认 `spring.profiles.active: local`，直接运行即可。
6. 控制台出现 `Started NexflowApplication` 即成功。

**命令行等价方式**

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
# 或先打包再运行（产物可独立部署）
mvn package && java -jar target/nexflow-backend-1.0.0.jar --spring.profiles.active=local
```

- 本地默认 H2 文件数据库（`backend/data/nexflow`，**重启数据不丢**，满足"重新启动后记录仍然存在"的要求）
- 首次运行自动播种演示数据（权限 / 角色 / 部门 / 用户 / 工单类型 / 自定义字段 / SLA / 分配规则 / FAQ / 约 30 张工单）
- 播种是幂等的：已有用户时直接跳过，不会重复写入
- H2 控制台：<http://localhost:8080/h2-console>（JDBC URL `jdbc:h2:file:./data/nexflow`，用户 `sa`，空密码）
- 想重新播种：删除 `backend/data` 目录后重启

### 前端（端口 5173）

```bash
cd frontend
npm install
npm run dev
```

Vite 已将 `/api` 代理到 `http://localhost:8080`（同源，因此开发态不依赖 CORS 配置）。

> **两种运行模式**
> - **全栈联调**：先后端后前端。前端会**优先调用真实 REST API**；登录、工单、审批、统计、配置全部走真实数据。
> - **离线演示**：只启动前端。API 不可达时自动回退到前端演示数据，并在页面上标注「演示数据（未连接后端）」，便于区分。

## 演示账号（数据均为演示数据）

### 前端离线演示模式（无需后端）

前端内置演示数据层。使用下列账号登录时会走本地演示数据，**不依赖后端**即可完整浏览全部页面：

| 账号 | 密码 | 角色 | 登录后落地页 |
|---|---|---|---|
| admin | admin123 | 管理员 | /app/dashboard（可见全部 8 项菜单） |
| supervisor | sup123 | 主管 | /app/dashboard |
| wangqiang | wq123 | 处理人 | /app/workbench |
| linxiao | lx123 | 普通员工 | /app/workbench（仅 1 项菜单） |

### 后端种子账号（联调模式）

后端播种的完整账号集合（连接真实 API 时可用）：

| 账号 | 密码 | 角色 |
|---|---|---|
| admin | admin123 | 管理员 |
| supervisor | sup123 | 主管 |
| wangqiang | wq123 | 处理人（IT） |
| limin | lm123 | 处理人（行政） |
| zhangting | zt123 | 处理人（人事） |
| zhaolei | zl123 | 处理人（设备） |
| linxiao | lx123 | 普通员工 |
| wangfang | wf123 | 普通员工 |

## 切换到 MySQL 8（生产）

1. 启动本机 MySQL 服务，创建库与账号（参考 `docker/mysql/init.sql`）
2. 以 `prod` profile 运行：`mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=prod`
3. 或用 Docker：`docker compose -f docker/docker-compose.yml up --build`

环境变量：`NEXFLOW_DB_URL` / `NEXFLOW_DB_USER` / `NEXFLOW_DB_PASSWORD` / `NEXFLOW_JWT_SECRET`。

## 测试

后端集成测试位于 `backend/src/test/java/com/nexflow/NexflowApiIntegrationTest.java`，
使用 `@SpringBootTest` + `MockMvc`，跑在独立的**内存 H2**（`test` profile）上，不会污染本地开发库。

```bash
cd backend
mvn test          # 仅测试
mvn package       # 测试 + 打包可执行 jar
```

**最近一次执行结果：`Tests run: 15, Failures: 0, Errors: 0, Skipped: 0` — BUILD SUCCESS**

| # | 用例 | 覆盖点 |
|---|---|---|
| 1 | 登录返回 JWT 与角色权限 | 认证、权限装载 |
| 2 | 未携带 token 访问受保护接口 → 401 | 服务端认证强制 |
| 3 | 配置中心接口统一包封 | 懒加载 500 的回归防线 |
| 4 | 通知列表包封与分页结构 | 响应契约一致性 |
| 5 | 普通员工访问配置中心 → 403 | RBAC 服务端强制 |
| 6 | 缺失必填自定义字段 → 拒绝 | 自定义字段校验 |
| 7 | 创建工单并持久化自定义字段值 | CRUD + 持久化 |
| 8 | **状态机全流程 创建→分类→自动分配→处理→解决→关闭** | 核心业务闭环 |
| 9 | **跨部门工单也能自动分配** | 分配规则匹配语义的回归防线 |
| 10 | **审批中工单必有待审批记录** | 种子数据一致性（防止工单卡死、审批中心为空） |
| 11 | **审批通过后工单离开待审批状态** | 审批对流程的实际约束 |
| 12 | 非法流转被拒绝且状态不变 | 状态机约束 |
| 13 | 评论写入并出现在详情中 | 评论持久化 |
| 14 | 新建工单后统计总量 +1 | 统计与数据库一致 |
| 15 | 列表分页结构与关键字过滤 | 查询能力 |

前端类型校验与构建：

```bash
cd frontend
npx vue-tsc --noEmit -p tsconfig.json   # 类型检查
npm run build                            # 生产构建
```

## REST API 一览

所有业务接口统一返回 `{ code, message, data, fieldErrors }`，`code = 0` 表示成功。

| 分组 | 路径前缀 | 说明 |
|---|---|---|
| 认证 | `/api/auth` | 登录、当前用户 |
| 工单 | `/api/tickets` | 列表 / 详情 / 创建 / 状态流转 / 批量 / 评论 |
| 审批 | `/api/approvals` | 待审批列表、审批决策 |
| 分配 | `/api/assignment-rules` | 规则 CRUD、候选人评估 `preview` |
| SLA | `/api/sla` | 策略列表 |
| 统计 | `/api/stats` | 概览 / 趋势 / 部门绩效 / 负载 / 队列 / SLA 风险 |
| 通知 | `/api/notifications` | 列表、已读，另有 SSE 流 `/api/notifications/stream` |
| 门户 | `/api/portal`、`/api/knowledge`、`/api/search` | 门户首页、FAQ、全局搜索 |
| 配置 | `/api/config` | 用户 / 部门 / 角色 / 工单类型 / 自定义字段 |
| 审计 | `/api/audit-logs` | 操作审计 |
| 文件 | `/api/files` | 附件上传下载 |
| AI | `/api/ai` | 可选的分类建议 / 摘要 / FAQ 推荐（`app.ai.enabled=false` 时核心功能不受影响） |

工单状态流转统一走 `POST /api/tickets/{id}/{action}`，`action` 取值与详情返回的
`legalActions[].action` 完全一致：`classify` / `assign` / `auto_assign` / `start` / `resolve` / `confirm` / `reopen` / `cancel`。
