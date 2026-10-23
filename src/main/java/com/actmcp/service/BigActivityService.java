package com.actmcp.service;

import com.actmcp.dto.ActivityOption;
import com.actmcp.dto.BigActivityBundleRequest;
import com.actmcp.dto.BigActivityBundleResponse;
import com.actmcp.dto.BigActivityQuery;
import com.actmcp.dto.BigActivityRequest;
import com.actmcp.dto.PageResult;
import com.actmcp.dto.ProductActivityItemRequest;
import com.actmcp.dto.ProductActivityRequest;
import com.actmcp.dto.Stats;
import com.actmcp.exception.BadRequestException;
import com.actmcp.exception.NotFoundException;
import com.actmcp.model.BigActivity;
import com.actmcp.model.ProductActivity;
import com.actmcp.repository.BigActivityRepository;
import com.actmcp.repository.ProductActivityRepository;
import tools.jackson.databind.ObjectMapper;
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
    private final ProductActivityService productActivityService;
    private final ObjectMapper objectMapper;

    public BigActivityService(BigActivityRepository bigActivityRepository,
                              ProductActivityRepository productActivityRepository,
                              ProductActivityService productActivityService,
                              ObjectMapper objectMapper) {
        this.bigActivityRepository = bigActivityRepository;
        this.productActivityRepository = productActivityRepository;
        this.productActivityService = productActivityService;
        this.objectMapper = objectMapper;
    }

    public PageResult<BigActivity> list(BigActivityQuery query) {
        int page = Math.max(query.getPage(), 1);
        int size = Math.min(Math.max(query.getSize(), 1), MAX_PAGE_SIZE);
        List<BigActivity> items = bigActivityRepository.findPage(
                query.getKeyword(), query.getActType(), query.getAudienceScope(), page, size);
        long total = bigActivityRepository.count(
                query.getKeyword(), query.getActType(), query.getAudienceScope());
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
    public BigActivity create(BigActivityRequest request) {
        if (request.id() != null) {
            throw new BadRequestException("新建大活动时不能指定 id");
        }
        String operator = ValidationUtils.normalizeOperator(request.operator());
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

    /**
     * 组合创建：先创建大活动，再把它下属的多个商品活动挂到新大活动下。
     * 任一步骤失败都会回滚整个事务，避免出现"只有大活动、没有商品活动"的中间状态。
     */
    @Transactional
    public BigActivityBundleResponse createWithProducts(BigActivityBundleRequest request) {
        BigActivityRequest bigRequest = request.bigActivity();
        if (bigRequest.id() != null) {
            throw new BadRequestException("新建大活动时不能指定 id");
        }

        BigActivity created = create(bigRequest);
        List<ProductActivity> products = request.productActivities().stream()
                .map(item -> productActivityService.create(toProductRequest(created.id(), bigRequest.operator(), item)))
                .toList();
        return new BigActivityBundleResponse(get(created.id()), products);
    }

    private ProductActivityRequest toProductRequest(long actId, String operator, ProductActivityItemRequest item) {
        // actType 传 null，由 ProductActivityService 统一继承大活动的活动类型
        return new ProductActivityRequest(
                null,
                actId,
                null,
                item.poiId(),
                item.skuId(),
                item.upc(),
                item.productDiscountJson(),
                operator
        );
    }

    @Transactional
    public BigActivity update(BigActivityRequest request) {
        if (request.id() == null) {
            throw new BadRequestException("大活动 id 不能为空");
        }
        BigActivity existing = get(request.id());
        String operator = ValidationUtils.normalizeOperator(request.operator());
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
        return get(request.id());
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
