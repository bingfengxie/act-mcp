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
