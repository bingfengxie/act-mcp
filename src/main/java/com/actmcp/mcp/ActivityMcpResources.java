package com.actmcp.mcp;

import com.actmcp.exception.BadRequestException;
import com.actmcp.service.BigActivityService;
import com.actmcp.service.ProductActivityService;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * MCP Resource 层：对外暴露只读的字典、规范与业务详情。
 * <p>
 * 固定 URI 资源用于提供稳定的参考数据；URI 模板资源用于按 ID 读取业务实体。
 */
@Component
public class ActivityMcpResources {

    private static final String ACT_TYPE_CATALOG_JSON = """
            {
              "schemaVersion": "1.0",
              "dataset": "act_type_catalog",
              "updatedAt": "2026-10-23",
              "items": [
                {"code": 1, "name": "满减活动", "description": "满足门槛金额后减免固定金额"},
                {"code": 2, "name": "折扣活动", "description": "按折扣率对商品进行降价"},
                {"code": 3, "name": "秒杀活动", "description": "限定时间段内的限量特价"},
                {"code": 4, "name": "优惠券活动", "description": "发放可核销的优惠券"},
                {"code": 5, "name": "组合优惠", "description": "多种优惠方式叠加配置"}
              ]
            }
            """;

    private static final String AUDIENCE_SCOPE_CATALOG_JSON = """
            {
              "schemaVersion": "1.0",
              "dataset": "audience_scope_catalog",
              "updatedAt": "2026-10-23",
              "items": [
                {"code": "ALL", "name": "所有用户", "description": "面向全量注册用户投放"},
                {"code": "NEW", "name": "新客", "description": "仅面向首次下单的新用户投放"}
              ]
            }
            """;

    /**
     * create_big_activity_with_products 的入参 Schema（JSON Schema 2020-12）。
     * <p>
     * 通过 x-form 扩展提供表单渲染所需的字段顺序、控件类型、下拉选项等描述，
     * 客户端可据此生成表单或校验参数，工具本身只保留动作语义。
     */
    private static final String CREATE_BIG_ACTIVITY_WITH_PRODUCTS_SCHEMA_JSON = """
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "activity://schemas/create-big-activity-with-products",
              "x-tool": "create_big_activity_with_products",
              "title": "组合创建活动（1 个大活动 + 多个商品活动）",
              "description": "create_big_activity_with_products 工具的入参 Schema。大活动与全部商品活动在同一事务内创建，任一商品活动失败时整体回滚。",
              "type": "object",
              "additionalProperties": false,
              "required": ["name", "actType", "startTime", "endTime", "audienceScope", "budget", "productActivitiesJson"],
              "properties": {
                "name": {
                  "type": "string",
                  "minLength": 1,
                  "maxLength": 100,
                  "description": "大活动名称，需体现业务场景。",
                  "x-form": {
                    "order": 1,
                    "label": "活动名称",
                    "widget": "input",
                    "placeholder": "例如：双十一全场满减",
                    "required": true
                  }
                },
                "actType": {
                  "type": "integer",
                  "description": "活动类型编码，下属商品活动自动继承该类型。",
                  "oneOf": [
                    {"const": 1, "title": "满减活动"},
                    {"const": 2, "title": "折扣活动"},
                    {"const": 3, "title": "秒杀活动"},
                    {"const": 4, "title": "优惠券活动"},
                    {"const": 5, "title": "组合优惠"}
                  ],
                  "x-form": {
                    "order": 2,
                    "label": "活动类型",
                    "widget": "select",
                    "required": true,
                    "options": [
                      {"value": 1, "label": "满减活动"},
                      {"value": 2, "label": "折扣活动"},
                      {"value": 3, "label": "秒杀活动"},
                      {"value": 4, "label": "优惠券活动"},
                      {"value": 5, "label": "组合优惠"}
                    ]
                  }
                },
                "startTime": {
                  "type": "string",
                  "pattern": "^\\\\d{4}-\\\\d{2}-\\\\d{2}[T ]\\\\d{2}:\\\\d{2}(:\\\\d{2}(\\\\.\\\\d+)?)?$",
                  "description": "开始时间，推荐格式 yyyy-MM-ddTHH:mm，也兼容 yyyy-MM-dd HH:mm(:ss)。",
                  "x-form": {
                    "order": 3,
                    "label": "开始时间",
                    "widget": "datetime",
                    "format": "yyyy-MM-ddTHH:mm",
                    "required": true
                  }
                },
                "endTime": {
                  "type": "string",
                  "pattern": "^\\\\d{4}-\\\\d{2}-\\\\d{2}[T ]\\\\d{2}:\\\\d{2}(:\\\\d{2}(\\\\.\\\\d+)?)?$",
                  "description": "结束时间，必须晚于开始时间；推荐格式 yyyy-MM-ddTHH:mm。",
                  "x-form": {
                    "order": 4,
                    "label": "结束时间",
                    "widget": "datetime",
                    "format": "yyyy-MM-ddTHH:mm",
                    "required": true
                  }
                },
                "audienceScope": {
                  "type": "string",
                  "description": "活动人群范围。",
                  "oneOf": [
                    {"const": "ALL", "title": "所有用户"},
                    {"const": "NEW", "title": "新客"}
                  ],
                  "x-form": {
                    "order": 5,
                    "label": "活动人群",
                    "widget": "radio",
                    "required": true,
                    "options": [
                      {"value": "ALL", "label": "所有用户"},
                      {"value": "NEW", "label": "新客"}
                    ]
                  }
                },
                "budget": {
                  "type": "number",
                  "minimum": 0,
                  "maximum": 999999999999.99,
                  "multipleOf": 0.01,
                  "description": "活动预算，单位元，最多 12 位整数与 2 位小数。",
                  "x-form": {
                    "order": 6,
                    "label": "预算（元）",
                    "widget": "number",
                    "step": 0.01,
                    "required": true
                  }
                },
                "collectionDiscountJson": {
                  "type": "string",
                  "default": "{}",
                  "contentMediaType": "application/json",
                  "description": "集合优惠 JSON 字符串，必须是合法 JSON；省略时按 {} 处理。",
                  "x-form": {
                    "order": 7,
                    "label": "集合优惠 JSON",
                    "widget": "json-editor",
                    "required": false
                  }
                },
                "productActivitiesJson": {
                  "type": "string",
                  "minLength": 2,
                  "contentMediaType": "application/json",
                  "contentSchema": {
                    "type": "array",
                    "minItems": 1,
                    "items": {"$ref": "#/$defs/productActivityItem"}
                  },
                  "description": "商品活动列表的 JSON 数组字符串，至少 1 个条目。条目结构见 $defs.productActivityItem；活动类型与所属大活动由服务端自动继承/绑定，无需传入 actType、actId。",
                  "x-form": {
                    "order": 8,
                    "label": "商品活动列表",
                    "widget": "json-editor",
                    "itemSchemaRef": "#/$defs/productActivityItem",
                    "required": true
                  }
                },
                "operator": {
                  "type": "string",
                  "maxLength": 50,
                  "default": "mcp-agent",
                  "description": "操作人名称，用于审计记录。",
                  "x-form": {
                    "order": 9,
                    "label": "操作人",
                    "widget": "input",
                    "placeholder": "默认 mcp-agent",
                    "required": false
                  }
                }
              },
              "$defs": {
                "productActivityItem": {
                  "title": "商品活动条目",
                  "type": "object",
                  "additionalProperties": false,
                  "required": ["poiId"],
                  "properties": {
                    "poiId": {
                      "oneOf": [
                        {"type": "integer", "minimum": 1},
                        {"type": "string", "pattern": "^[1-9]\\\\d*$"}
                      ],
                      "description": "POI ID，64 位正整数。建议以字符串传递，避免超过 Number.MAX_SAFE_INTEGER 后精度丢失。",
                      "x-form": {"label": "POI ID", "widget": "text", "required": true}
                    },
                    "skuId": {
                      "oneOf": [
                        {"type": "integer", "minimum": 1},
                        {"type": "string", "pattern": "^[1-9]\\\\d*$"}
                      ],
                      "description": "SKU ID，64 位正整数，可选。建议以字符串传递。",
                      "x-form": {"label": "SKU ID", "widget": "text", "required": false}
                    },
                    "upc": {
                      "type": "string",
                      "maxLength": 64,
                      "description": "UPC 编码，可选。",
                      "x-form": {"label": "UPC", "widget": "input", "required": false}
                    },
                    "productDiscountJson": {
                      "type": "string",
                      "default": "{}",
                      "contentMediaType": "application/json",
                      "description": "商品优惠 JSON 字符串，必须是合法 JSON；省略时按 {} 处理。",
                      "x-form": {"label": "商品优惠 JSON", "widget": "json-editor", "required": false}
                    }
                  },
                  "x-form": {
                    "label": "商品活动",
                    "widget": "table",
                    "itemLabel": "商品活动条目"
                  }
                }
              },
              "x-business-rules": [
                "endTime 必须晚于 startTime",
                "productActivitiesJson 至少包含 1 个商品活动条目",
                "poiId 必填且为正整数，skuId 可选但同样必须是正整数",
                "collectionDiscountJson、productDiscountJson 必须是合法 JSON，省略时按 {} 处理",
                "整个组合创建在同一事务内完成，任一商品活动失败则全部回滚"
              ],
              "examples": [
                {
                  "name": "双十一专场",
                  "actType": 1,
                  "startTime": "2026-11-01T00:00",
                  "endTime": "2026-11-12T23:59",
                  "audienceScope": "ALL",
                  "budget": 50000,
                  "collectionDiscountJson": "{\\\"threshold\\\":100,\\\"discount\\\":20}",
                  "productActivitiesJson": "[{\\\"poiId\\\":\\\"1001\\\",\\\"skuId\\\":\\\"9001\\\",\\\"productDiscountJson\\\":\\\"{\\\\\\\"price\\\\\\\":9.9}\\\"},{\\\"poiId\\\":\\\"1002\\\"}]",
                  "operator": "alice"
                }
              ]
            }
            """;

    private static final String OPERATIONS_HANDBOOK_MARKDOWN = """
            # 活动配置管理规范

            ## 1. 适用范围
            本规范适用于大活动（Big Activity）与商品活动（Product Activity）的创建、变更与下线。

            ## 2. 字段规范
            | 字段 | 约束 | 说明 |
            | --- | --- | --- |
            | name | 必填，2-64 字符 | 活动名称需体现业务场景，禁止重复 |
            | actType | 必填，取值 1-5 | 见活动类型字典资源 |
            | startTime / endTime | 必填，yyyy-MM-ddTHH:mm | 结束时间必须晚于开始时间 |
            | audienceScope | 必填，ALL / NEW | 见活动人群字典资源 |
            | budget | 必填，大于 0 | 单位：元 |
            | collectionDiscountJson | 可选，合法 JSON | 为空时按 {} 处理 |
            | poiId | 必填，64 位整数 | 建议以字符串传递，避免精度丢失 |
            | skuId | 可选，64 位整数 | 建议以字符串传递，避免精度丢失 |

            ## 3. 变更流程
            1. 创建前完成预算与人群确认。
            2. 变更需通过 update 接口提交，并填写 operator。
            3. 下线活动必须显式删除，删除大活动将级联删除其商品活动。

            ## 4. 数据质量要求
            - 所有 JSON 字段入库前必须通过合法性校验。
            - 时间字段统一使用 Asia/Shanghai 时区。
            - 关键写操作需保留操作人与操作时间。
            """;

    private final BigActivityService bigActivityService;
    private final ProductActivityService productActivityService;
    private final ObjectMapper objectMapper;

    public ActivityMcpResources(BigActivityService bigActivityService,
                                ProductActivityService productActivityService,
                                ObjectMapper objectMapper) {
        this.bigActivityService = bigActivityService;
        this.productActivityService = productActivityService;
        this.objectMapper = objectMapper;
    }

    @McpResource(name = "act-type-catalog",
            title = "活动类型字典",
            uri = "activity://catalog/act-types",
            description = "活动类型编码与含义的权威字典，用于校验与展示 actType 字段。",
            mimeType = "application/json")
    public String actTypeCatalog() {
        return ACT_TYPE_CATALOG_JSON;
    }

    @McpResource(name = "audience-scope-catalog",
            title = "活动人群字典",
            uri = "activity://catalog/audience-scopes",
            description = "活动人群编码与含义的权威字典，用于校验与展示 audienceScope 字段。",
            mimeType = "application/json")
    public String audienceScopeCatalog() {
        return AUDIENCE_SCOPE_CATALOG_JSON;
    }

    @McpResource(name = "activity-operations-handbook",
            title = "活动配置管理规范",
            uri = "activity://docs/operations-handbook",
            description = "大活动与商品活动的字段规范、变更流程与数据质量要求。",
            mimeType = "text/markdown")
    public String operationsHandbook() {
        return OPERATIONS_HANDBOOK_MARKDOWN;
    }

    @McpResource(name = "create-big-activity-with-products-schema",
            title = "组合创建活动入参 Schema",
            uri = "activity://schemas/create-big-activity-with-products",
            description = "create_big_activity_with_products 工具的入参 Schema 与表单描述，"
                    + "包含字段约束、枚举选项、商品活动条目结构与调用示例，供客户端生成表单或校验参数。",
            mimeType = "application/schema+json")
    public String createBigActivityWithProductsSchema() {
        return CREATE_BIG_ACTIVITY_WITH_PRODUCTS_SCHEMA_JSON;
    }

    @McpResource(name = "big-activity-detail",
            title = "大活动详情",
            uri = "activity://big-activities/{id}",
            description = "按 ID 读取大活动详情，返回 JSON，字段与 get_big_activity 工具一致。",
            mimeType = "application/json")
    public String bigActivityDetail(String id) {
        return objectMapper.writeValueAsString(bigActivityService.get(parseId(id, "大活动 id")));
    }

    @McpResource(name = "product-activity-detail",
            title = "商品活动详情",
            uri = "activity://product-activities/{id}",
            description = "按 ID 读取商品活动详情，返回 JSON，字段与 get_product_activity 工具一致。",
            mimeType = "application/json")
    public String productActivityDetail(String id) {
        return objectMapper.writeValueAsString(productActivityService.get(parseId(id, "商品活动 id")));
    }

    private long parseId(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(fieldName + "不能为空");
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            throw new BadRequestException(fieldName + "必须是有效的 64 位整数");
        }
    }
}
