package com.actmcp.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ProductActivityRequest(
        @NotNull(message = "所属大活动不能为空")
        Long actId,

        @Min(value = 1, message = "活动类型必须是 1-5 的整数")
        @Max(value = 5, message = "活动类型必须是 1-5 的整数")
        Integer actType,

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
