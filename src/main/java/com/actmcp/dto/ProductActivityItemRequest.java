package com.actmcp.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 组合创建时的商品活动条目。
 * 所属大活动与活动类型都由外层大活动决定，因此不包含 actId 和 actType。
 */
public record ProductActivityItemRequest(
        @NotNull(message = "POI ID 不能为空")
        @Positive(message = "POI ID 必须是正整数")
        Long poiId,

        @Positive(message = "SKU ID 必须是正整数")
        Long skuId,

        @Size(max = 64, message = "UPC 不能超过 64 个字符")
        String upc,

        String productDiscountJson
) {
}
