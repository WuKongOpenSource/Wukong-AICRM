# sync_data 数据同步模块说明

`sync_data` 是一个独立的数据同步模块，用于把老悟空 CRM（`wk_crm` MySQL）的数据同步到新 AI CRM（PostgreSQL）。模块位于 `D:\workspace\AI_CRM\sync_data`，不依赖主后端业务 Service，也不触发主后端里的登录态、租户拦截器、权限校验、AI 分析等业务逻辑。

当前重点能力是：在 AI CRM 侧选择并绑定老库指定 `company_id`，然后由前端通过 HTTP 接口触发同步，只同步这个 `company_id` 下的数据。

## 模块定位

- 独立 Maven/Spring Boot 工程，可单独编译和运行。
- 仅以 HTTP 服务模式提供同步能力，启动应用本身不会执行全量同步。
- 通过 `sync_mapping` 保存老库数据和新库数据的 ID 映射，保证重复执行时可幂等 upsert。
- 通过 `sync_company_binding` 保存 `AI CRM tenant_id <-> WK CRM company_id` 的绑定关系。
- 已预留 MQ 增量同步入口，但当前 MQ 客户端只做占位，尚未接入具体 RabbitMQ、RocketMQ 或 Kafka。

## 目录结构

```text
sync_data
├── pom.xml
├── README.md
└── src/main
    ├── java/com/kakarote/syncdata
    │   ├── SyncDataApplication.java       # HTTP 服务启动入口，启动时不自动同步
    │   ├── SyncProperties.java            # sync.* 配置绑定
    │   ├── config
    │   │   └── DataSourceConfig.java      # 老库 MySQL / 新库 PostgreSQL 双数据源
    │   ├── controller
    │   │   ├── SyncBindingController.java # 绑定、全量同步、任务查询接口
    │   │   └── IncrementalSyncController.java # 增量事件预留接口
    │   ├── db
    │   │   ├── TargetSchema.java          # 同步元数据表和兼容字段自动初始化
    │   │   ├── MappingRepository.java     # ID 映射、任务、错误记录
    │   │   └── CompanyBindingRepository.java # company 绑定关系读写
    │   ├── service
    │   │   ├── FullSyncService.java       # 全量同步主流程
    │   │   └── CompanyBindingService.java # 绑定流程和老库 company 列表
    │   ├── incremental
    │   │   ├── IncrementalSyncEvent.java
    │   │   ├── IncrementalSyncService.java
    │   │   └── MqIncrementalSyncConsumer.java
    │   ├── model
    │   └── util
    └── resources
        └── application.yml
```

## 数据源配置

默认配置在 `src/main/resources/application.yml`：

| 配置项 | 说明 |
| --- | --- |
| `sync.old-crm.jdbc-url` | 老 WK CRM MySQL 地址，默认 `127.0.0.1:3306/wk_crm` |
| `sync.old-crm.username` | 老库账号，默认 `root` |
| `sync.old-crm.password` | 老库密码，默认 `Admin001m` |
| `sync.target.jdbc-url` | 新 AI CRM PostgreSQL 地址 |
| `sync.target.username` | 新库账号 |
| `sync.target.password` | 新库密码 |
| `sync.batch-size` | 分页同步大小，默认 `500` |
| `sync.dry-run` | HTTP 触发同步时只统计源表数量，不写目标库 |
| `sync.truncate-before-sync` | 同步前删除当前映射创建过的目标数据 |
| `sync.populate-search-index` | 同步后是否刷新全局搜索索引 |
| `sync.reset-password` | 迁移用户写入的新 BCrypt 默认密码 |

建议生产环境通过环境变量覆盖敏感信息：

```powershell
$env:SYNC_OLD_JDBC_URL="jdbc:mysql://127.0.0.1:3306/wk_crm?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&zeroDateTimeBehavior=convertToNull"
$env:SYNC_OLD_USERNAME="root"
$env:SYNC_OLD_PASSWORD="Admin001m"
$env:SYNC_TARGET_JDBC_URL="jdbc:postgresql://127.0.0.1:5432/wk_ai_crm_saas"
$env:SYNC_TARGET_USERNAME="postgres"
$env:SYNC_TARGET_PASSWORD="postgres"
```

## 同步范围

当前已实现的核心表映射：

| 老 WK CRM 表 | 新 AI CRM 表 | 说明 |
| --- | --- | --- |
| `wk_admin_company` | `crm_tenant` | 全库模式下创建租户；绑定模式下使用已绑定 `tenant_id` |
| `wk_admin_dept` | `manager_dept` | 部门 |
| `wk_admin_user` | `manager_user` | 用户，密码统一重置为 BCrypt 默认密码 |
| `wk_admin_role` | `manager_role` | 角色基础信息 |
| `wk_admin_user_role` | `manager_user_role` | 用户角色关系 |
| `wk_crm_field` | `crm_custom_field` | 客户/联系人自定义字段定义 |
| `wk_crm_customer` | `crm_customer` | 客户 |
| `wk_crm_contacts` | `crm_contact` | 联系人 |
| `wk_crm_customer_data` | `crm_customer` 动态列 | 客户自定义字段值 |
| `wk_crm_contacts_data` | `crm_contact` 动态列 | 联系人自定义字段值 |
| `wk_crm_activity` | `crm_follow_up` | 跟进记录，仅同步跟进类活动 |
| `wk_oa_event` | `crm_schedule` | 日程 |
| `wk_project_task` | `crm_task` | 项目任务 |
| `wk_work_task` | `crm_task` | 工作台任务 |

暂未同步：商机、合同、回款、产品、审批等老库模块。原因是当前 AI CRM 尚没有完整对应目标表，后续需要先确定新库业务模型，再扩展表映射和转换器。

## 元数据表

模块启动同步前，会在目标 PostgreSQL 自动创建同步元数据表。
同步元数据表主键统一由 `SnowflakeIdGenerator` 生成，不依赖 PostgreSQL `BIGSERIAL` / sequence。
如果历史环境中这些表曾经使用过 `BIGSERIAL`，模块初始化时会自动移除主键字段上的 sequence 默认值。

| 表名 | 用途 |
| --- | --- |
| `sync_company_binding` | 保存 `tenant_id` 与老库 `company_id` 的绑定关系 |
| `sync_full_job` | 全量同步任务主表 |
| `sync_job_module` | 每个同步模块的数量、成功数、失败数 |
| `sync_job_error` | 单条数据同步失败明细 |
| `sync_mapping` | 老库主键与新库主键映射关系 |
| `sync_incremental_event_log` | MQ/HTTP 增量事件消费日志预留 |

`sync_mapping` 的唯一键为：

```text
source_system + source_table + source_company_id + source_id + target_table
```

因此同一条老数据重复同步时会复用同一个新库 ID，并对目标表执行 upsert。

## 全量同步逻辑

全量同步主流程在 `FullSyncService`：

1. 检查老库核心表是否存在。
2. `dry-run` 模式下只统计源表数量，不写目标库。
3. 初始化目标库同步元数据表和兼容字段。
4. 根据模式确定同步范围：
   - 全库模式：读取老库所有 `company_id`。
   - 绑定模式：只同步绑定的单个 `company_id`。
5. 按依赖顺序同步：
   - 租户/绑定租户
   - 部门
   - 角色
   - 用户
   - 用户角色
   - 自定义字段定义
   - 客户
   - 联系人
   - 自定义字段值
   - 跟进记录
   - 日程
   - 任务
6. 每个模块分页读取，默认一页 `500` 条。
7. 单条数据失败写入 `sync_job_error`，不中断整个模块。
8. 同步完成后刷新客户冗余字段：
   - `primary_contact_name`
   - `primary_contact_phone`
   - `primary_contact_position`
   - `contact_count`
   - `search_text`
9. 如果目标库存在 `crm_global_search_index`，则刷新客户、联系人、任务、日程搜索索引。

## 绑定模式

绑定模式用于 AICRM 页面选择老库指定 `company_id` 后，只同步该企业的数据。

绑定关系写入 `sync_company_binding`：

```text
tenant_id = AI CRM 租户 ID
source_company_id = WK CRM company_id
source_system = wk_crm
```

绑定后的同步会给所有老库源表自动追加过滤：

```sql
company_id = :sourceCompanyId
```

这样可以避免把多个老企业的数据同步到同一个 AI CRM 租户。

## 启动 HTTP 服务

进入模块目录：

```powershell
cd D:\workspace\AI_CRM\sync_data
```

编译：

```powershell
mvn -DskipTests package
```

启动服务：

```powershell
java -jar target\sync-data-1.0.0.jar --server.port=10456
```

服务默认端口可通过 `SYNC_SERVER_PORT` 或 `--server.port` 调整。启动服务只会开放 HTTP API，不会自动执行全量同步；同步必须由 AICRM 前端调用绑定和启动同步接口触发。

如需调大分页大小或同步前清理当前映射创建过的数据，可在启动服务时配置：

```powershell
java -jar target\sync-data-1.0.0.jar --server.port=10456 --sync.batch-size=1000 --sync.truncate-before-sync=true
```

注意：`truncate-before-sync=true` 只删除 `sync_mapping` 记录中对应的目标数据，不会删除用户手工创建且没有映射的数据。

### 查询老库可绑定 company

```http
GET /sync/old-companies
```

可选参数：

- `managerPhone`：传入手机号后，仅返回 `wk_admin_company.company_manage = managerPhone` 的企业，用于 AICRM 页面按管理员手机号选择可绑定企业。

返回示例：

```json
[
  {
    "companyId": 1754439814505484288,
    "companyName": "WK CRM 1754439814505484288",
    "customerCount": 5224,
    "contactCount": 662,
    "userCount": 84,
    "followUpCount": 28051
  }
]
```

### 创建或更新绑定

```http
POST /sync/bindings
Content-Type: application/json
```

请求体：

```json
{
  "tenantId": 1,
  "companyId": 1754439814505484288,
  "incrementalEnabled": true,
  "mqTopic": "wk-crm-binlog",
  "mqGroup": "ai-crm-sync-data",
  "remark": "首次迁移绑定"
}
```

PowerShell 示例：

```powershell
Invoke-RestMethod -Method Post -Uri http://127.0.0.1:10456/sync/bindings `
  -ContentType 'application/json' `
  -Body '{"tenantId":1,"companyId":1754439814505484288,"incrementalEnabled":true}'
```

### 查询绑定

```http
GET /sync/bindings
```

### 触发绑定全量同步

```http
POST /sync/bindings/{bindingId}/full-sync
```

示例：

```powershell
Invoke-RestMethod -Method Post -Uri http://127.0.0.1:10456/sync/bindings/1/full-sync
```

### 查询任务状态

```http
GET /sync/jobs/{jobId}
GET /sync/jobs/{jobId}/modules
GET /sync/jobs/{jobId}/errors
```

`/errors` 当前最多返回最近 `200` 条错误。

## 增量 MQ 预留

当前已预留以下增量同步结构：

- `sync_incremental_event_log`
- `IncrementalSyncEvent`
- `IncrementalSyncService`
- `MqIncrementalSyncConsumer`
- `POST /sync/incremental/events`

配置项：

```yaml
sync:
  incremental:
    mq:
      enabled: false
      topic: wk-crm-binlog
      consumer-group: ai-crm-sync-data
```

当前状态：

- 已能按 `sourceCompanyId` 找到 `sync_company_binding`。
- 已能判断该绑定是否开启 `incrementalEnabled`。
- 已能把事件写入 `sync_incremental_event_log`。
- 已预留 MQ 消息转换入口 `MqIncrementalSyncConsumer#onMessage`。
- 尚未接入具体 MQ 客户端。
- 尚未把增量事件转换为目标表 insert/update/delete。

建议的 MQ 消息结构：

```json
{
  "sourceSystem": "wk_crm",
  "sourceCompanyId": 1754439814505484288,
  "sourceTable": "wk_crm_customer",
  "sourceId": "2030000000000000001",
  "operation": "UPDATE",
  "traceId": "binlog-20260429-0001",
  "payload": {
    "customer_id": 2030000000000000001,
    "company_id": 1754439814505484288,
    "customer_name": "示例客户"
  }
}
```

后续接入增量时建议按这个方向扩展：

1. 接入 RabbitMQ/RocketMQ/Kafka 客户端。
2. 将 MQ 原始消息转换为 `IncrementalSyncEvent`。
3. 根据 `sourceCompanyId` 找到绑定租户。
4. 根据 `sourceTable` 路由到对应转换器。
5. 复用 `sync_mapping` 获取或创建目标 ID。
6. 对目标表执行 upsert 或软删除。
7. 写入消费状态和 offset。
8. 使用 `traceId` / `originSystem` 避免双向同步时回环。

## 核心转换规则

用户：

- 不迁移老系统密码。
- 所有迁移用户写入新的 BCrypt 密码，默认 `ChangeMe@123`。
- 可通过 `--sync.reset-password=新密码` 修改。
- 找不到负责人时，会自动创建或复用“数据同步用户”。

客户：

- `customer_name` -> `company_name`
- `deal_status=1` -> `stage=closed`
- 其他成交状态 -> `stage=lead`
- `status=3` -> 新库 `status=0`
- `address + location + detail_address` 合并为新库地址。

联系人：

- `contacts_id` 通过 `sync_mapping` 映射为新联系人 ID。
- 根据 `wk_crm_customer.contacts_id` 判断主联系人。
- `mobile` / `telephone` 合并为 `phone`。

自定义字段：

- 只同步 `label IN (2, 3)`，也就是客户和联系人字段。
- 客户字段落到 `crm_customer`。
- 联系人字段落到 `crm_contact`。
- 目标动态列名为 `cf_wk_{companyId}_{fieldId}`，避免不同企业字段冲突。

跟进记录：

- 来源为 `wk_crm_activity`。
- 当前只同步 `type = 1` 且 `activity_type IN (2, 3)` 的跟进类记录。
- 关联客户或联系人时，会通过 `sync_mapping` 找到新库目标 ID。

任务：

- `wk_project_task` 和 `wk_work_task` 都落到 `crm_task`。
- `task_type` 分别标记为 `project` 或 `work`。

## 验证结果

当前已验证：

```powershell
mvn -q -DskipTests package
```

通过。

```powershell
java -jar target\sync-data-1.0.0.jar --server.port=10456
```

通过。启动后不会自动同步，只提供 HTTP API。

接口验证：

- `GET /sync/old-companies` 可返回老库 company 列表。
- `GET /sync/old-companies?managerPhone=手机号` 可按 `wk_admin_company.company_manage` 过滤。
- `POST /sync/bindings/{bindingId}/full-sync` 才会真正触发指定绑定企业的全量同步。

## 注意事项

- 当前同步模块直接写数据库，不调用主后端 Service。
- 应用启动不会自动执行同步，只能由 AICRM 前端通过 HTTP 接口触发。
- 第一次真实同步前，可临时以 `--sync.dry-run=true` 启动服务，再通过前端触发一次验证。
- 如果目标 PostgreSQL 是线上库，建议先在测试库完整演练。
- 默认同步服务端口为 `10456`，如被占用可使用 `--server.port=其他端口` 覆盖。
- 老库 `wk_admin_company` 当前可能为空，服务会通过各业务表的 `company_id` 聚合可绑定 company。
- 真实全量同步会自动创建同步元数据表，并可能给 `crm_customer` / `crm_contact` 添加自定义字段动态列。
- 增量 MQ 当前只是预留骨架，不代表已经能消费真实 binlog 并落库。

## 常见问题

### 为什么不用老库 ID 作为新库 ID？

老库很多表存在 `company_id + id` 的复合主键语义，新库是全局单 ID。直接复用容易冲突，也不利于后续多租户隔离。因此模块使用 Snowflake 生成新 ID，并通过 `sync_mapping` 保存对应关系。

### 重复运行会不会产生重复数据？

正常不会。只要 `sync_mapping` 保留，重复运行会复用已有目标 ID，并对目标表执行 upsert。

### 同步失败如何排查？

查看：

```sql
SELECT * FROM sync_full_job ORDER BY job_id DESC;
SELECT * FROM sync_job_module WHERE job_id = ? ORDER BY id;
SELECT * FROM sync_job_error WHERE job_id = ? ORDER BY id DESC;
```

服务模式下也可以调用：

```http
GET /sync/jobs/{jobId}
GET /sync/jobs/{jobId}/modules
GET /sync/jobs/{jobId}/errors
```

### 如何只重同步某个 company？

在 AICRM 同步页选择该企业并重新触发全量同步；如需重同步前删除当前映射创建过的数据，启动服务时加入：

```powershell
java -jar target\sync-data-1.0.0.jar --server.port=10456 --sync.truncate-before-sync=true
```

这会删除该 `company_id` 在 `sync_mapping` 中映射过的目标数据，然后重新同步。不会删除无映射的手工数据。
