package com.actmcp.repository;

import com.actmcp.model.ProductActivity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class ProductActivityRepository {

    private static final String SELECT_WITH_ACT_NAME = """
            SELECT p.id, p.act_id, a.name AS act_name, p.act_type, p.poi_id, p.sku_id, p.upc,
                   p.product_discount_json, p.created_by, p.created_at, p.updated_by, p.updated_at
            FROM product_activity p
            JOIN big_activity a ON a.id = p.act_id
            """;

    private static final RowMapper<ProductActivity> ROW_MAPPER = (rs, rowNum) -> new ProductActivity(
            rs.getLong("id"),
            rs.getLong("act_id"),
            rs.getString("act_name"),
            rs.getString("act_type"),
            rs.getString("poi_id"),
            rs.getString("sku_id"),
            rs.getString("upc"),
            rs.getString("product_discount_json"),
            rs.getString("created_by"),
            rs.getString("created_at"),
            rs.getString("updated_by"),
            rs.getString("updated_at")
    );

    private final JdbcTemplate jdbcTemplate;

    public ProductActivityRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ProductActivity> findPage(Long actId, String actType, String keyword,
                                          int page, int size) {
        List<Object> args = new ArrayList<>();
        String where = buildWhere(actId, actType, keyword, args);
        args.add(size);
        args.add((page - 1) * size);
        String sql = SELECT_WITH_ACT_NAME + where + " ORDER BY p.id DESC LIMIT ? OFFSET ?";
        return jdbcTemplate.query(sql, ROW_MAPPER, args.toArray());
    }

    public long count(Long actId, String actType, String keyword) {
        List<Object> args = new ArrayList<>();
        String where = buildWhere(actId, actType, keyword, args);
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product_activity p JOIN big_activity a ON a.id = p.act_id " + where,
                Long.class, args.toArray());
        return total == null ? 0 : total;
    }

    public Optional<ProductActivity> findById(long id) {
        List<ProductActivity> rows = jdbcTemplate.query(
                SELECT_WITH_ACT_NAME + " WHERE p.id = ?", ROW_MAPPER, id);
        return rows.stream().findFirst();
    }

    public long insert(ProductActivity activity) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO product_activity
                        (act_id, act_type, poi_id, sku_id, upc, product_discount_json,
                         created_by, created_at, updated_by, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, activity.actId());
            ps.setString(2, activity.actType());
            ps.setString(3, activity.poiId());
            ps.setString(4, activity.skuId());
            ps.setString(5, activity.upc());
            ps.setString(6, activity.productDiscountJson());
            ps.setString(7, activity.createdBy());
            ps.setString(8, activity.createdAt());
            ps.setString(9, activity.updatedBy());
            ps.setString(10, activity.updatedAt());
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("创建商品活动失败：未获取到主键");
        }
        return key.longValue();
    }

    public int update(ProductActivity activity) {
        return jdbcTemplate.update("""
                        UPDATE product_activity
                        SET act_id = ?, act_type = ?, poi_id = ?, sku_id = ?, upc = ?,
                            product_discount_json = ?, updated_by = ?, updated_at = ?
                        WHERE id = ?
                        """,
                activity.actId(), activity.actType(), activity.poiId(), activity.skuId(),
                activity.upc(), activity.productDiscountJson(), activity.updatedBy(),
                activity.updatedAt(), activity.id());
    }

    public int deleteById(long id) {
        return jdbcTemplate.update("DELETE FROM product_activity WHERE id = ?", id);
    }

    public int deleteByActId(long actId) {
        return jdbcTemplate.update("DELETE FROM product_activity WHERE act_id = ?", actId);
    }

    public long countAll() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM product_activity", Long.class);
        return count == null ? 0 : count;
    }

    private String buildWhere(Long actId, String actType, String keyword, List<Object> args) {
        List<String> conditions = new ArrayList<>();
        if (actId != null) {
            conditions.add("p.act_id = ?");
            args.add(actId);
        }
        if (actType != null && !actType.isBlank()) {
            conditions.add("p.act_type = ?");
            args.add(actType.trim());
        }
        if (keyword != null && !keyword.isBlank()) {
            String pattern = "%" + BigActivityRepository.escapeLike(keyword.trim()) + "%";
            conditions.add("(p.poi_id LIKE ? ESCAPE '\\' OR p.sku_id LIKE ? ESCAPE '\\' "
                    + "OR p.upc LIKE ? ESCAPE '\\' OR a.name LIKE ? ESCAPE '\\')");
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
        }
        return conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
    }
}
