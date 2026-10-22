package com.actmcp.model;

import java.math.BigDecimal;

public record BigActivity(
        Long id,
        String name,
        Integer actType,
        String startTime,
        String endTime,
        String audienceScope,
        BigDecimal budget,
        String collectionDiscountJson,
        String createdBy,
        String createdAt,
        String updatedBy,
        String updatedAt,
        long productCount
) {
}
