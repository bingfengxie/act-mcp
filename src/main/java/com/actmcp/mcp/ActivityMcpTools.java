package com.actmcp.mcp;

import com.actmcp.dto.BigActivityQuery;
import com.actmcp.dto.BigActivityRequest;
import com.actmcp.dto.PageResult;
import com.actmcp.dto.ProductActivityQuery;
import com.actmcp.dto.ProductActivityRequest;
import com.actmcp.dto.Stats;
import com.actmcp.exception.BadRequestException;
import com.actmcp.model.BigActivity;
import com.actmcp.model.ProductActivity;
import com.actmcp.service.BigActivityService;
import com.actmcp.service.ProductActivityService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * MCP 工具层：只做参数适配与校验，业务逻辑复用现有 Service。
 */
@Component
public class ActivityMcpTools {

    private static final String DEFAULT_OPERATOR = "mcp-agent";

    private final BigActivityService bigActivityService;
    private final ProductActivityService productActivityService;
    private final Validator validator;

    public ActivityMcpTools(BigActivityService bigActivityService,
                            ProductActivityService productActivityService,
                            Validator validator) {
        this.bigActivityService = bigActivityService;
        this.productActivityService = productActivityService;
        this.validator = validator;
    }

    @Tool(name = "list_big_activities",
            description = "分页查询大活动，可按名称关键字、活动类型和活动人群筛选，并返回每个活动的商品活动数量。")
    public PageResult<BigActivity> listBigActivities(
            @ToolParam(required = false, description = "页码，从 1 开始，默认 1") Integer page,
            @ToolParam(required = false, description = "每页数量，默认 10，最大 100") Integer size,
            @ToolParam(required = false, description = "大活动名称关键字") String keyword,
            @ToolParam(required = false, description = "活动类型：1=满减，2=折扣，3=秒杀，4=优惠券，5=组合") Integer actType,
            @ToolParam(required = false, description = "活动人群：ALL=所有用户，NEW=新客") String audienceScope) {
        BigActivityQuery query = new BigActivityQuery();
        query.setPage(page == null ? 1 : page);
        query.setSize(size == null ? 10 : size);
        query.setKeyword(keyword);
        query.setActType(actType);
        query.setAudienceScope(audienceScope);
        return bigActivityService.list(query);
    }

    @Tool(name = "get_big_activity", description = "根据 id 查询大活动详情，包含集合优惠 JSON。")
    public BigActivity getBigActivity(
            @ToolParam(description = "大活动 id") Long id) {
        return bigActivityService.get(requireId(id, "大活动 id"));
    }

    @Tool(name = "create_big_activity",
            description = "创建大活动。时间为 yyyy-MM-ddTHH:mm 格式；集合优惠 JSON 需为合法 JSON 字符串。")
    public BigActivity createBigActivity(
            @ToolParam(description = "活动名称") String name,
            @ToolParam(description = "活动类型：1=满减，2=折扣，3=秒杀，4=优惠券，5=组合") Integer actType,
            @ToolParam(description = "开始时间，格式 yyyy-MM-ddTHH:mm") String startTime,
            @ToolParam(description = "结束时间，格式 yyyy-MM-ddTHH:mm") String endTime,
            @ToolParam(description = "活动人群：ALL=所有用户，NEW=新客") String audienceScope,
            @ToolParam(description = "预算，单位元") BigDecimal budget,
            @ToolParam(required = false, description = "集合优惠 JSON 字符串，默认 {}") String collectionDiscountJson,
            @ToolParam(required = false, description = "操作人，默认 mcp-agent") String operator) {
        BigActivityRequest request = new BigActivityRequest(
                null,
                name,
                actType,
                startTime,
                endTime,
                audienceScope,
                budget,
                collectionDiscountJson == null ? "{}" : collectionDiscountJson,
                operatorOrDefault(operator)
        );
        validate(request);
        return bigActivityService.create(request);
    }

    @Tool(name = "update_big_activity",
            description = "根据 id 更新大活动，仅传入需要修改的字段，未传字段保持原值。")
    public BigActivity updateBigActivity(
            @ToolParam(description = "大活动 id") Long id,
            @ToolParam(required = false, description = "活动名称") String name,
            @ToolParam(required = false, description = "活动类型：1=满减，2=折扣，3=秒杀，4=优惠券，5=组合") Integer actType,
            @ToolParam(required = false, description = "开始时间，格式 yyyy-MM-ddTHH:mm") String startTime,
            @ToolParam(required = false, description = "结束时间，格式 yyyy-MM-ddTHH:mm") String endTime,
            @ToolParam(required = false, description = "活动人群：ALL=所有用户，NEW=新客") String audienceScope,
            @ToolParam(required = false, description = "预算，单位元") BigDecimal budget,
            @ToolParam(required = false, description = "集合优惠 JSON 字符串") String collectionDiscountJson,
            @ToolParam(required = false, description = "操作人，默认 mcp-agent") String operator) {
        long activityId = requireId(id, "大活动 id");
        BigActivity existing = bigActivityService.get(activityId);
        BigActivityRequest request = new BigActivityRequest(
                activityId,
                name == null ? existing.name() : name,
                actType == null ? existing.actType() : actType,
                startTime == null ? existing.startTime() : startTime,
                endTime == null ? existing.endTime() : endTime,
                audienceScope == null ? existing.audienceScope() : audienceScope,
                budget == null ? existing.budget() : budget,
                collectionDiscountJson == null ? existing.collectionDiscountJson() : collectionDiscountJson,
                operatorOrDefault(operator)
        );
        validate(request);
        return bigActivityService.update(request);
    }

    @Tool(name = "delete_big_activity", description = "删除大活动，并级联删除该活动下的所有商品活动。")
    public McpOperationResult deleteBigActivity(
            @ToolParam(description = "大活动 id") Long id) {
        long activityId = requireId(id, "大活动 id");
        bigActivityService.delete(activityId);
        return new McpOperationResult(true, "大活动及其关联商品活动已删除", activityId);
    }

    @Tool(name = "list_product_activities",
            description = "分页查询商品活动，可按所属大活动、活动类型以及活动名/POI/SKU/UPC 关键字筛选。")
    public PageResult<ProductActivity> listProductActivities(
            @ToolParam(required = false, description = "页码，从 1 开始，默认 1") Integer page,
            @ToolParam(required = false, description = "每页数量，默认 10，最大 100") Integer size,
            @ToolParam(required = false, description = "所属大活动 id") Long actId,
            @ToolParam(required = false, description = "活动类型：1=满减，2=折扣，3=秒杀，4=优惠券，5=组合") Integer actType,
            @ToolParam(required = false, description = "关键字，可匹配大活动名称、POI、SKU、UPC") String keyword) {
        ProductActivityQuery query = new ProductActivityQuery();
        query.setPage(page == null ? 1 : page);
        query.setSize(size == null ? 10 : size);
        query.setActId(actId);
        query.setActType(actType);
        query.setKeyword(keyword);
        return productActivityService.list(query);
    }

    @Tool(name = "get_product_activity", description = "根据 id 查询商品活动详情，包含商品优惠 JSON。")
    public ProductActivity getProductActivity(
            @ToolParam(description = "商品活动 id") Long id) {
        return productActivityService.get(requireId(id, "商品活动 id"));
    }

    @Tool(name = "create_product_activity",
            description = "创建商品活动。poiId 必填，skuId 可选，均为 64 位整数字符串；actType 不传时继承大活动类型。")
    public ProductActivity createProductActivity(
            @ToolParam(description = "所属大活动 id") Long actId,
            @ToolParam(required = false, description = "活动类型：1=满减，2=折扣，3=秒杀，4=优惠券，5=组合；不传则继承大活动") Integer actType,
            @ToolParam(description = "POI ID，64 位整数，以字符串传递") String poiId,
            @ToolParam(required = false, description = "SKU ID，64 位整数，以字符串传递") String skuId,
            @ToolParam(required = false, description = "UPC 编码") String upc,
            @ToolParam(required = false, description = "商品优惠 JSON 字符串，默认 {}") String productDiscountJson,
            @ToolParam(required = false, description = "操作人，默认 mcp-agent") String operator) {
        ProductActivityRequest request = new ProductActivityRequest(
                null,
                actId,
                actType,
                parseRequiredLong(poiId, "POI ID"),
                parseOptionalLong(skuId, "SKU ID"),
                upc,
                productDiscountJson == null ? "{}" : productDiscountJson,
                operatorOrDefault(operator)
        );
        validate(request);
        return productActivityService.create(request);
    }

    @Tool(name = "update_product_activity",
            description = "根据 id 更新商品活动，仅传入需要修改的字段。SKU ID 传空字符串可清空。")
    public ProductActivity updateProductActivity(
            @ToolParam(description = "商品活动 id") Long id,
            @ToolParam(required = false, description = "所属大活动 id") Long actId,
            @ToolParam(required = false, description = "活动类型：1=满减，2=折扣，3=秒杀，4=优惠券，5=组合") Integer actType,
            @ToolParam(required = false, description = "POI ID，64 位整数，以字符串传递") String poiId,
            @ToolParam(required = false, description = "SKU ID，64 位整数，以字符串传递；传空字符串清空") String skuId,
            @ToolParam(required = false, description = "UPC 编码，传空字符串清空") String upc,
            @ToolParam(required = false, description = "商品优惠 JSON 字符串") String productDiscountJson,
            @ToolParam(required = false, description = "操作人，默认 mcp-agent") String operator) {
        long productId = requireId(id, "商品活动 id");
        ProductActivity existing = productActivityService.get(productId);

        Long resolvedActId = actId == null ? existing.actId() : actId;
        Integer resolvedActType = actType;
        if (resolvedActType == null) {
            resolvedActType = (actId != null && !actId.equals(existing.actId()))
                    ? bigActivityService.get(actId).actType()
                    : existing.actType();
        }

        ProductActivityRequest request = new ProductActivityRequest(
                productId,
                resolvedActId,
                resolvedActType,
                poiId == null ? existing.poiId() : parseRequiredLong(poiId, "POI ID"),
                skuId == null ? existing.skuId() : parseOptionalLong(skuId, "SKU ID"),
                upc == null ? existing.upc() : upc,
                productDiscountJson == null ? existing.productDiscountJson() : productDiscountJson,
                operatorOrDefault(operator)
        );
        validate(request);
        return productActivityService.update(request);
    }

    @Tool(name = "delete_product_activity", description = "根据 id 删除商品活动。")
    public McpOperationResult deleteProductActivity(
            @ToolParam(description = "商品活动 id") Long id) {
        long productId = requireId(id, "商品活动 id");
        productActivityService.delete(productId);
        return new McpOperationResult(true, "商品活动已删除", productId);
    }

    @Tool(name = "get_activity_stats", description = "获取大活动总数、商品活动总数、进行中活动数和活动总预算。")
    public Stats getActivityStats() {
        return bigActivityService.stats();
    }

    private long requireId(Long id, String fieldName) {
        if (id == null) {
            throw new BadRequestException(fieldName + "不能为空");
        }
        return id;
    }

    private String operatorOrDefault(String operator) {
        return operator == null || operator.isBlank() ? DEFAULT_OPERATOR : operator.trim();
    }

    private Long parseRequiredLong(String value, String fieldName) {
        Long parsed = parseOptionalLong(value, fieldName);
        if (parsed == null) {
            throw new BadRequestException(fieldName + "不能为空");
        }
        return parsed;
    }

    private Long parseOptionalLong(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            throw new BadRequestException(fieldName + "必须是有效的 64 位整数");
        }
    }

    private <T> void validate(T target) {
        Set<ConstraintViolation<T>> violations = validator.validate(target);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .sorted(Comparator.comparing(v -> v.getPropertyPath().toString()))
                    .map(ConstraintViolation::getMessage)
                    .distinct()
                    .collect(Collectors.joining("；"));
            throw new BadRequestException(message);
        }
    }
}
