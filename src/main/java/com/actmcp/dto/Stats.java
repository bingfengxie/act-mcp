package com.actmcp.dto;

import java.math.BigDecimal;

public record Stats(
        long bigActivityCount,
        long productActivityCount,
        long ongoingCount,
        BigDecimal totalBudget
) {
}
