package com.actmcp.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

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
