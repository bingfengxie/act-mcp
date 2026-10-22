package com.actmcp.dto;

import java.util.List;

public record PageResult<T>(
        List<T> items,
        long total,
        int page,
        int size,
        int totalPages
) {
    public static <T> PageResult<T> of(List<T> items, long total, int page, int size) {
        int totalPages = size > 0 ? (int) ((total + size - 1) / size) : 0;
        return new PageResult<>(items, total, page, size, totalPages);
    }
}
