package com.actmcp.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record BigActivityRequest(
        @NotBlank(message = "活动名称不能为空")
        @Size(max = 100, message = "活动名称不能超过 100 个字符")
        String name,

        @NotNull(message = "活动类型不能为空")
        @Min(value = 1, message = "活动类型必须是 1-5 的整数")
        @Max(value = 5, message = "活动类型必须是 1-5 的整数")
        Integer actType,

        @NotBlank(message = "开始时间不能为空")
        String startTime,

        @NotBlank(message = "结束时间不能为空")
        String endTime,

        @NotBlank(message = "活动人群不能为空")
        @Pattern(regexp = "ALL|NEW", message = "活动人群只能是 ALL（所有用户）或 NEW（新客）")
        String audienceScope,

        @NotNull(message = "预算不能为空")
        @DecimalMin(value = "0", message = "预算不能小于 0")
        @Digits(integer = 12, fraction = 2, message = "预算最多 12 位整数和 2 位小数")
        BigDecimal budget,

        String collectionDiscountJson
) {
}
