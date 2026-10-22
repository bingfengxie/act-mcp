package com.actmcp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProductActivityRequest(
        @NotNull(message = "所属大活动不能为空")
        Long actId,

        @Size(max = 50, message = "活动类型不能超过 50 个字符")
        String actType,

        @NotBlank(message = "POI ID 不能为空")
        @Size(max = 64, message = "POI ID 不能超过 64 个字符")
        String poiId,

        @Size(max = 64, message = "SKU ID 不能超过 64 个字符")
        String skuId,

        @Size(max = 64, message = "UPC 不能超过 64 个字符")
        String upc,

        String productDiscountJson
) {
}
