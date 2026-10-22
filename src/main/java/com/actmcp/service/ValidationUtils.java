package com.actmcp.service;

import com.actmcp.exception.BadRequestException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

final class ValidationUtils {

    private static final DateTimeFormatter STORAGE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter MINUTE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private ValidationUtils() {
    }

    static LocalDateTime parseDateTime(String raw, String fieldName) {
        if (raw == null || raw.isBlank()) {
            throw new BadRequestException(fieldName + "不能为空");
        }
        String value = raw.trim();
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            // 尝试常见的空格分隔格式
        }
        try {
            return LocalDateTime.parse(value, STORAGE_FORMAT);
        } catch (DateTimeParseException ignored) {
            // 尝试精确到分钟的格式
        }
        try {
            return LocalDateTime.parse(value, MINUTE_FORMAT);
        } catch (DateTimeParseException ignored) {
            throw new BadRequestException(fieldName + "格式不正确，应为 yyyy-MM-ddTHH:mm");
        }
    }

    static String formatDateTime(LocalDateTime dateTime) {
        return dateTime.format(STORAGE_FORMAT);
    }

    static String normalizeJson(ObjectMapper objectMapper, String raw, String fieldName) {
        if (raw == null || raw.isBlank()) {
            return "{}";
        }
        String value = raw.trim();
        try {
            JsonNode node = objectMapper.readTree(value);
            if (node == null || node.isMissingNode()) {
                throw new BadRequestException(fieldName + "不是合法的 JSON");
            }
            return value;
        } catch (JacksonException ex) {
            throw new BadRequestException(fieldName + "不是合法的 JSON：" + ex.getOriginalMessage());
        }
    }

    static String normalizeOperator(String operator) {
        if (operator == null || operator.isBlank()) {
            return "admin";
        }
        String value = operator.trim();
        if (value.length() > 50) {
            throw new BadRequestException("操作人名称不能超过 50 个字符");
        }
        return value;
    }

    static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
