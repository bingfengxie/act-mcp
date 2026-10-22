# Act MCP · 活动管理平台

一个基于 Spring Boot + SQLite 的活动管理 Web 系统，支持对大活动与商品活动两张表进行完整的增删改查，并提供筛选、分页、表单校验、JSON 优惠配置查看等能力。

## 技术栈

- Java 21
- Spring Boot 3.3（Spring MVC、Spring JDBC、Bean Validation）
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

浏览器访问：<http://localhost:8080>

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
  port: 8080

spring:
  datasource:
    url: jdbc:sqlite:./data/act-mcp.db
```

- 修改端口：启动时追加 `--server.port=9090`
- 修改数据库位置：调整 `spring.datasource.url`，或启动时传入 `--spring.datasource.url=jdbc:sqlite:/path/to/act-mcp.db`
- SQLite 已为每个连接启用 `PRAGMA foreign_keys = ON`；删除大活动时服务层也会显式清理关联商品，避免历史库未开启外键时产生脏数据

## 数据表

### big_activity（大活动）

| 字段 | 说明 |
| --- | --- |
| id | 主键，自增 |
| name | 活动名称 |
| act_type | 活动类型 |
| start_time / end_time | 开始 / 结束时间（`yyyy-MM-dd HH:mm:ss`） |
| audience_scope | `ALL` 所有用户 / `NEW` 新客 |
| budget | 预算（元） |
| collection_discount_json | 集合优惠 JSON |
| created_by / created_at | 创建人 / 创建时间 |
| updated_by / updated_at | 修改人 / 最后修改时间 |

### product_activity（商品活动）

| 字段 | 说明 |
| --- | --- |
| id | 主键，自增 |
| act_id | 外键，关联 `big_activity.id`，级联删除 |
| act_type | 活动类型（留空时自动继承大活动类型） |
| poi_id | POI ID |
| sku_id / upc | SKU ID / UPC（可选） |
| product_discount_json | 商品优惠 JSON |
| created_by / created_at | 创建人 / 创建时间 |
| updated_by / updated_at | 修改人 / 最后修改时间 |

## API

所有写操作通过请求头 `X-Operator` 传递操作人；未传时记为 `admin`。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/big-activities` | 分页查询大活动，支持 `page`、`size`、`keyword`、`actType`、`audienceScope` |
| GET | `/api/big-activities/options` | 获取大活动下拉选项 |
| GET | `/api/big-activities/{id}` | 查询大活动详情 |
| POST | `/api/big-activities` | 新建大活动 |
| PUT | `/api/big-activities/{id}` | 更新大活动 |
| DELETE | `/api/big-activities/{id}` | 删除大活动（级联删除商品活动） |
| GET | `/api/product-activities` | 分页查询商品活动，支持 `page`、`size`、`actId`、`actType`、`keyword` |
| GET | `/api/product-activities/{id}` | 查询商品活动详情 |
| POST | `/api/product-activities` | 新建商品活动 |
| PUT | `/api/product-activities/{id}` | 更新商品活动 |
| DELETE | `/api/product-activities/{id}` | 删除商品活动 |
| GET | `/api/stats` | 统计数据 |

示例：

```bash
curl -X POST http://localhost:8080/api/big-activities \
  -H 'Content-Type: application/json' \
  -H 'X-Operator: alice' \
  -d '{
    "name": "双十一全场满减",
    "actType": "满减活动",
    "startTime": "2026-11-01T00:00",
    "endTime": "2026-11-12T23:59",
    "audienceScope": "ALL",
    "budget": 50000,
    "collectionDiscountJson": "{\"threshold\":100,\"discount\":20}"
  }'
```

时间字段接受 `yyyy-MM-ddTHH:mm`、`yyyy-MM-dd HH:mm`、`yyyy-MM-dd HH:mm:ss` 格式，统一按 `yyyy-MM-dd HH:mm:ss` 存储与返回。

## 项目结构

```
act-mcp/
├── pom.xml
├── src/main/java/com/actmcp/
│   ├── ActMcpApplication.java
│   ├── controller/        # REST 接口
│   ├── dto/               # 请求对象、分页结果
│   ├── exception/         # 统一异常处理
│   ├── model/             # 数据模型
│   ├── repository/        # Spring JDBC 数据访问
│   └── service/           # 业务逻辑与校验
├── src/main/resources/
│   ├── application.yml
│   ├── schema.sql         # 建表脚本
│   └── static/            # 前端页面
└── src/test/java/         # 接口集成测试
```
