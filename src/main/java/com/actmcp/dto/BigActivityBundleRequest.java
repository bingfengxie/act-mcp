package com.actmcp.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 组合创建请求：一次创建 1 个大活动与其下属的多个商品活动。
 */
public record BigActivityBundleRequest(
        @NotNull(message = "大活动信息不能为空")
        @Valid
        BigActivityRequest bigActivity,

        @NotEmpty(message = "至少需要创建一个商品活动")
        List<@Valid ProductActivityItemRequest> productActivities
) {
}
