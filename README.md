# Act MCP · 活动管理平台

一个基于 Spring Boot + SQLite 的活动管理平台，同时提供 Web UI、REST API 与 MCP 接口，支持对大活动与商品活动两张表进行完整的增删改查，并提供筛选、分页、表单校验、JSON 优惠配置查看等能力。

## 技术栈

- Java 21
- Spring Boot 4.1（Spring MVC、Spring JDBC、Bean Validation）
- Spring AI 2.0 MCP Server（Streamable HTTP）
- SQLite（`sqlite-jdbc` 驱动，数据文件本地持久化）
- 原生 HTML / CSS / JavaScript（无需 Node.js 构建）

## 功能

### 大活动管理

- 活动名称、活动类型、开始/结束时间、活动人群（所有用户 / 新客专享）、预算
- 集合优惠 JSON 配置，支持格式化与完整预览
- 按名称、类型、人群筛选，分页浏览
- 编辑、删除；删除时级联清理关联的商品活动
- 一键跳转到该活动下的商品活动列表

### 商品活动管理

- 绑定所属大活动，维护 POI ID、SKU ID、UPC 与活动类型
- 商品优惠 JSON 配置，支持格式化与完整预览
- 按大活动、类型，以及活动名 / POI / SKU / UPC 关键字筛选
- 编辑、删除

### Agent / MCP 管理

- 通过标准 MCP Streamable HTTP 协议连接 Agent
- 提供大活动、商品活动的查询、创建、部分更新、删除工具
- 与 Web UI、REST API 共用同一套 Service、校验规则和数据库
- 写操作自动记录 Agent 传入的 `operator`

### 其他

- 顶部统计卡片：大活动数、商品活动数、进行中活动、总预算
- 操作人记录：创建人 / 创建时间、修改人 / 最后修改时间自动维护
- 统一参数校验与友好的错误提示
- 响应式布局，适配桌面与移动端

## 快速开始

环境要求：JDK 21+、Maven 3.9+。

```bash
cd act-mcp

# 开发模式启动
mvn spring-boot:run
```

浏览器访问：<http://localhost:8090>

也可以先打包再运行：

```bash
mvn clean package
java -jar target/act-mcp-1.0.0.jar
```

首次启动会自动创建 `data/act-mcp.db` 并初始化数据表。

运行测试：

```bash
mvn test
```

## 配置

主要配置位于 `src/main/resources/application.yml`：

```yaml
server:
  port: 8090

spring:
  datasource:
    url: jdbc:sqlite:./data/act-mcp.db
```

- 修改端口：启动时追加 `--server.port=9090`
- 修改数据库位置：调整 `spring.datasource.url`，或启动时传入 `--spring.datasource.url=jdbc:sqlite:/path/to/act-mcp.db`
- SQLite 已为每个连接启用 `PRAGMA foreign_keys = ON`；删除大活动时服务层也会显式清理关联商品，避免历史库未开启外键时产生脏数据

### 旧数据库升级

如果已有使用旧表结构（`act_type`、`poi_id`、`sku_id` 为 TEXT）的数据库，先停止服务并备份，再执行：

```bash
sqlite3 data/act-mcp.db < scripts/migrate-v2-int-types.sql
```

脚本会保留原有数据并自动完成类型与活动类型编码转换。

## 数据表

### 活动类型编码

| 编码 | 类型 |
| --- | --- |
| 1 | 满减活动 |
| 2 | 折扣活动 |
| 3 | 秒杀活动 |
| 4 | 优惠券活动 |
| 5 | 组合优惠 |

`act_type` 在两张表中均为 `INTEGER`。

### big_activity（大活动）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | INTEGER | 主键，自增 |
| name | TEXT | 活动名称 |
| act_type | INTEGER | 活动类型编码（1-5） |
| start_time / end_time | TEXT | 开始 / 结束时间（`yyyy-MM-dd HH:mm:ss`） |
| audience_scope | TEXT | `ALL` 所有用户 / `NEW` 新客 |
| budget | NUMERIC | 预算（元） |
| collection_discount_json | TEXT | 集合优惠 JSON |
| created_by / created_at | TEXT | 创建人 / 创建时间 |
| updated_by / updated_at | TEXT | 修改人 / 最后修改时间 |

### product_activity（商品活动）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | INTEGER | 主键，自增 |
| act_id | INTEGER | 外键，关联 `big_activity.id`，级联删除 |
| act_type | INTEGER | 活动类型编码（留空时自动继承大活动类型） |
| poi_id | INTEGER | POI ID（64 位整数） |
| sku_id | INTEGER | SKU ID（64 位整数，可选） |
| upc | TEXT | UPC（可选） |
| product_discount_json | TEXT | 商品优惠 JSON |
| created_by / created_at | TEXT | 创建人 / 创建时间 |
| updated_by / updated_at | TEXT | 修改人 / 最后修改时间 |

> `poi_id`、`sku_id` 在 JSON 接口中以字符串形式传输，避免 JavaScript 超过 `Number.MAX_SAFE_INTEGER` 后丢失精度；后端仍按 64 位整数存储和校验。

## API

所有 Controller 方法最多接收一个参数对象。写操作的 `operator` 统一放在请求体中；更新操作的 `id` 也放在请求体中，`PUT` 请求发往集合路径。`operator` 未传时记为 `admin`。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/big-activities` | 分页查询大活动，支持 `page`、`size`、`keyword`、`actType`、`audienceScope` |
| GET | `/api/big-activities/options` | 获取大活动下拉选项 |
| GET | `/api/big-activities/{id}` | 查询大活动详情 |
| POST | `/api/big-activities` | 新建大活动 |
| PUT | `/api/big-activities` | 更新大活动（`id` 在请求体） |
| DELETE | `/api/big-activities/{id}` | 删除大活动（级联删除商品活动） |
| GET | `/api/product-activities` | 分页查询商品活动，支持 `page`、`size`、`actId`、`actType`、`keyword` |
| GET | `/api/product-activities/{id}` | 查询商品活动详情 |
| POST | `/api/product-activities` | 新建商品活动 |
| PUT | `/api/product-activities` | 更新商品活动（`id` 在请求体） |
| DELETE | `/api/product-activities/{id}` | 删除商品活动 |
| GET | `/api/stats` | 统计数据 |

示例：

```bash
curl -X POST http://localhost:8090/api/big-activities \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "双十一全场满减",
    "actType": 1,
    "startTime": "2026-11-01T00:00",
    "endTime": "2026-11-12T23:59",
    "audienceScope": "ALL",
    "budget": 50000,
    "collectionDiscountJson": "{\"threshold\":100,\"discount\":20}",
    "operator": "alice"
  }'
```

更新示例：

```bash
curl -X PUT http://localhost:8090/api/big-activities \
  -H 'Content-Type: application/json' \
  -d '{
    "id": 1,
    "name": "双十一全场满减（调整）",
    "actType": 1,
    "startTime": "2026-11-01T00:00",
    "endTime": "2026-11-15T23:59",
    "audienceScope": "ALL",
    "budget": 60000,
    "collectionDiscountJson": "{}",
    "operator": "bob"
  }'
```

时间字段接受 `yyyy-MM-ddTHH:mm`、`yyyy-MM-dd HH:mm`、`yyyy-MM-dd HH:mm:ss` 格式，统一按 `yyyy-MM-dd HH:mm:ss` 存储与返回。

## MCP 接口（Agent 管理）

MCP Server 使用 Spring AI 2.0，传输协议为 **Streamable HTTP**，与现有 Web UI / REST API 运行在同一端口：

```
http://localhost:8090/mcp
```

Server 名称 `act-mcp`，版本 `1.0.0`，对外暴露 11 个工具。

### 工具列表

| 工具 | 说明 |
| --- | --- |
| `list_big_activities` | 分页筛选大活动 |
| `get_big_activity` | 查询大活动详情 |
| `create_big_activity` | 创建大活动 |
| `update_big_activity` | 部分更新大活动，未传字段保持原值 |
| `delete_big_activity` | 删除大活动并级联删除商品活动 |
| `list_product_activities` | 分页筛选商品活动 |
| `get_product_activity` | 查询商品活动详情 |
| `create_product_activity` | 创建商品活动 |
| `update_product_activity` | 部分更新商品活动，`skuId` 传空字符串可清空 |
| `delete_product_activity` | 删除商品活动 |
| `get_activity_stats` | 查询活动统计 |

工具参数说明：

- `actType`：`1` 满减、`2` 折扣、`3` 秒杀、`4` 优惠券、`5` 组合
- `audienceScope`：`ALL` 所有用户、`NEW` 新客
- 时间格式：`yyyy-MM-ddTHH:mm`
- `poiId`、`skuId`：64 位整数，以字符串传递
- `operator`：操作人，未传时记为 `mcp-agent`

### 连接 Agent

Claude Code：

```bash
claude mcp add --transport http act-mcp http://localhost:8090/mcp
```

支持 Streamable HTTP 的通用客户端配置：

```json
{
  "mcpServers": {
    "act-mcp": {
      "type": "http",
      "url": "http://localhost:8090/mcp"
    }
  }
}
```

仅支持 stdio 的客户端可以通过 `mcp-remote` 桥接：

```json
{
  "mcpServers": {
    "act-mcp": {
      "command": "npx",
      "args": ["-y", "mcp-remote", "http://localhost:8090/mcp"]
    }
  }
}
```

MCP 工具直接复用现有 Service，因此与 Web 操作共享相同的 JSON 校验、时间校验、权限外审计字段和级联删除逻辑。

## 接口清单（Actuator）

系统引入了 Spring Boot Actuator，并仅对外暴露 `mappings` 端点，可用于查看 Spring MVC 当前注册的所有后端接口：

```
GET http://localhost:8090/actuator/mappings
```

返回 JSON 中的 `contexts.application.mappings.dispatcherServlets` 节点包含每个 Controller 方法、请求方法和路径映射。相关配置：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: mappings
  endpoint:
    mappings:
      enabled: true
```

## 项目结构

```
act-mcp/
├── pom.xml
├── scripts/               # 旧库类型迁移脚本
├── src/main/java/com/actmcp/
│   ├── ActMcpApplication.java
│   ├── controller/        # REST 接口
│   ├── dto/               # 请求对象、分页结果
│   ├── exception/         # 统一异常处理
│   ├── mcp/               # MCP 工具与 Server 配置
│   ├── model/             # 数据模型
│   ├── repository/        # Spring JDBC 数据访问
│   └── service/           # 业务逻辑与校验
├── src/main/resources/
│   ├── application.yml
│   ├── schema.sql         # 建表脚本
│   └── static/            # 前端页面
└── src/test/java/         # 接口集成测试
```

## 说明

- 当前系统面向本地或内网使用，未内置登录鉴权。REST API、`/mcp` 和 `/actuator` 均未鉴权；如需公网部署，必须在 Spring Security 或网关层补充认证与访问控制。
- 页面右上角的「操作人」及 MCP 的 `operator` 参数仅用于记录创建人 / 修改人，不等同于身份认证。
- 数据库文件默认位于 `data/act-mcp.db`，备份时复制该文件即可（建议先停止服务）。

