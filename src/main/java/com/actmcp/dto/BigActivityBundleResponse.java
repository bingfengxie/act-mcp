package com.actmcp.dto;

import com.actmcp.model.BigActivity;
import com.actmcp.model.ProductActivity;

import java.util.List;

/**
 * 组合创建结果：新建的大活动及其下属商品活动。
 */
public record BigActivityBundleResponse(
        BigActivity bigActivity,
        List<ProductActivity> productActivities
) {
}
