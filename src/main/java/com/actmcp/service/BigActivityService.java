package com.actmcp.service;

import com.actmcp.dto.ActivityOption;
import com.actmcp.dto.BigActivityRequest;
import com.actmcp.dto.PageResult;
import com.actmcp.dto.Stats;
import com.actmcp.exception.BadRequestException;
import com.actmcp.exception.NotFoundException;
import com.actmcp.model.BigActivity;
import com.actmcp.repository.BigActivityRepository;
import com.actmcp.repository.ProductActivityRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class BigActivityService {

    private static final int MAX_PAGE_SIZE = 100;

    private final BigActivityRepository bigActivityRepository;
    private final ProductActivityRepository productActivityRepository;
    private final ObjectMapper objectMapper;

    public BigActivityService(BigActivityRepository bigActivityRepository,
                              ProductActivityRepository productActivityRepository,
                              ObjectMapper objectMapper) {
        this.bigActivityRepository = bigActivityRepository;
        this.productActivityRepository = productActivityRepository;
        this.objectMapper = objectMapper;
    }

    public PageResult<BigActivity> list(int page, int size, String keyword,
                                        Integer actType, String audienceScope) {
        page = Math.max(page, 1);
        size = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        List<BigActivity> items = bigActivityRepository.findPage(
                keyword, actType, audienceScope, page, size);
        long total = bigActivityRepository.count(keyword, actType, audienceScope);
        return PageResult.of(items, total, page, size);
    }

    public BigActivity get(long id) {
        return bigActivityRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("大活动不存在：" + id));
    }

    public List<ActivityOption> options() {
        return bigActivityRepository.findAllOptions().stream()
                .map(a -> new ActivityOption(a.id(), a.name(), a.actType()))
                .toList();
    }

    @Transactional
    public BigActivity create(BigActivityRequest request, String operatorHeader) {
        String operator = ValidationUtils.normalizeOperator(operatorHeader);
        LocalDateTime start = ValidationUtils.parseDateTime(request.startTime(), "开始时间");
        LocalDateTime end = ValidationUtils.parseDateTime(request.endTime(), "结束时间");
        validateTimeRange(start, end);

        String now = ValidationUtils.formatDateTime(LocalDateTime.now());
        BigActivity activity = new BigActivity(
                null,
                request.name().trim(),
                request.actType(),
                ValidationUtils.formatDateTime(start),
                ValidationUtils.formatDateTime(end),
                request.audienceScope(),
                normalizeBudget(request.budget()),
                ValidationUtils.normalizeJson(objectMapper, request.collectionDiscountJson(), "集合优惠 JSON"),
                operator,
                now,
                operator,
                now,
                0L
        );
        long id = bigActivityRepository.insert(activity);
        return get(id);
    }

    @Transactional
    public BigActivity update(long id, BigActivityRequest request, String operatorHeader) {
        BigActivity existing = get(id);
        String operator = ValidationUtils.normalizeOperator(operatorHeader);
        LocalDateTime start = ValidationUtils.parseDateTime(request.startTime(), "开始时间");
        LocalDateTime end = ValidationUtils.parseDateTime(request.endTime(), "结束时间");
        validateTimeRange(start, end);

        BigActivity updated = new BigActivity(
                existing.id(),
                request.name().trim(),
                request.actType(),
                ValidationUtils.formatDateTime(start),
                ValidationUtils.formatDateTime(end),
                request.audienceScope(),
                normalizeBudget(request.budget()),
                ValidationUtils.normalizeJson(objectMapper, request.collectionDiscountJson(), "集合优惠 JSON"),
                existing.createdBy(),
                existing.createdAt(),
                operator,
                ValidationUtils.formatDateTime(LocalDateTime.now()),
                existing.productCount()
        );
        bigActivityRepository.update(updated);
        return get(id);
    }

    @Transactional
    public void delete(long id) {
        get(id);
        // 显式删除关联商品活动，确保在未启用外键的旧库上也能安全级联
        productActivityRepository.deleteByActId(id);
        bigActivityRepository.deleteById(id);
    }

    public Stats stats() {
        String now = ValidationUtils.formatDateTime(LocalDateTime.now());
        return new Stats(
                bigActivityRepository.countAll(),
                productActivityRepository.countAll(),
                bigActivityRepository.countOngoing(now),
                bigActivityRepository.sumBudget()
        );
    }

    private void validateTimeRange(LocalDateTime start, LocalDateTime end) {
        if (!end.isAfter(start)) {
            throw new BadRequestException("结束时间必须晚于开始时间");
        }
    }

    private BigDecimal normalizeBudget(BigDecimal budget) {
        if (budget == null) {
            return BigDecimal.ZERO;
        }
        return budget.setScale(2, RoundingMode.HALF_UP);
    }
}
