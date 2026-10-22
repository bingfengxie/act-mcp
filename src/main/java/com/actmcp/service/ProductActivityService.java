package com.actmcp.service;

import com.actmcp.dto.PageResult;
import com.actmcp.dto.ProductActivityRequest;
import com.actmcp.exception.BadRequestException;
import com.actmcp.exception.NotFoundException;
import com.actmcp.model.BigActivity;
import com.actmcp.model.ProductActivity;
import com.actmcp.repository.BigActivityRepository;
import com.actmcp.repository.ProductActivityRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ProductActivityService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ProductActivityRepository productActivityRepository;
    private final BigActivityRepository bigActivityRepository;
    private final ObjectMapper objectMapper;

    public ProductActivityService(ProductActivityRepository productActivityRepository,
                                  BigActivityRepository bigActivityRepository,
                                  ObjectMapper objectMapper) {
        this.productActivityRepository = productActivityRepository;
        this.bigActivityRepository = bigActivityRepository;
        this.objectMapper = objectMapper;
    }

    public PageResult<ProductActivity> list(int page, int size, Long actId,
                                            String actType, String keyword) {
        page = Math.max(page, 1);
        size = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        List<ProductActivity> items = productActivityRepository.findPage(
                actId, actType, keyword, page, size);
        long total = productActivityRepository.count(actId, actType, keyword);
        return PageResult.of(items, total, page, size);
    }

    public ProductActivity get(long id) {
        return productActivityRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("商品活动不存在：" + id));
    }

    @Transactional
    public ProductActivity create(ProductActivityRequest request, String operatorHeader) {
        String operator = ValidationUtils.normalizeOperator(operatorHeader);
        BigActivity parent = requireParent(request.actId());
        String now = ValidationUtils.formatDateTime(LocalDateTime.now());
        String actType = resolveActType(request.actType(), parent);

        ProductActivity activity = new ProductActivity(
                null,
                parent.id(),
                parent.name(),
                actType,
                request.poiId().trim(),
                ValidationUtils.trimToNull(request.skuId()),
                ValidationUtils.trimToNull(request.upc()),
                ValidationUtils.normalizeJson(objectMapper, request.productDiscountJson(), "商品优惠 JSON"),
                operator,
                now,
                operator,
                now
        );
        long id = productActivityRepository.insert(activity);
        return get(id);
    }

    @Transactional
    public ProductActivity update(long id, ProductActivityRequest request, String operatorHeader) {
        ProductActivity existing = get(id);
        String operator = ValidationUtils.normalizeOperator(operatorHeader);
        BigActivity parent = requireParent(request.actId());
        String actType = resolveActType(request.actType(), parent);

        ProductActivity updated = new ProductActivity(
                existing.id(),
                parent.id(),
                parent.name(),
                actType,
                request.poiId().trim(),
                ValidationUtils.trimToNull(request.skuId()),
                ValidationUtils.trimToNull(request.upc()),
                ValidationUtils.normalizeJson(objectMapper, request.productDiscountJson(), "商品优惠 JSON"),
                existing.createdBy(),
                existing.createdAt(),
                operator,
                ValidationUtils.formatDateTime(LocalDateTime.now())
        );
        productActivityRepository.update(updated);
        return get(id);
    }

    @Transactional
    public void delete(long id) {
        get(id);
        productActivityRepository.deleteById(id);
    }

    private BigActivity requireParent(Long actId) {
        if (actId == null) {
            throw new BadRequestException("所属大活动不能为空");
        }
        return bigActivityRepository.findById(actId)
                .orElseThrow(() -> new BadRequestException("所属大活动不存在：" + actId));
    }

    private String resolveActType(String requested, BigActivity parent) {
        return requested == null || requested.isBlank() ? parent.actType() : requested.trim();
    }
}
