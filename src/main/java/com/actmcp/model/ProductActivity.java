package com.actmcp.model;

public record ProductActivity(
        Long id,
        Long actId,
        String actName,
        String actType,
        String poiId,
        String skuId,
        String upc,
        String productDiscountJson,
        String createdBy,
        String createdAt,
        String updatedBy,
        String updatedAt
) {
}
