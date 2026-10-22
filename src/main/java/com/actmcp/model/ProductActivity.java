package com.actmcp.model;

import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

public record ProductActivity(
        Long id,
        Long actId,
        String actName,
        Integer actType,
        @JsonSerialize(using = ToStringSerializer.class) Long poiId,
        @JsonSerialize(using = ToStringSerializer.class) Long skuId,
        String upc,
        String productDiscountJson,
        String createdBy,
        String createdAt,
        String updatedBy,
        String updatedAt
) {
}
