package com.actmcp.repository;

import com.actmcp.model.BigActivity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class BigActivityRepository {

    private static final String SELECT_WITH_COUNT = """
            SELECT a.id, a.name, a.act_type, a.start_time, a.end_time, a.audience_scope,
                   a.budget, a.collection_discount_json, a.created_by, a.created_at,
                   a.updated_by, a.updated_at,
                   (SELECT COUNT(*) FROM product_activity p WHERE p.act_id = a.id) AS product_count
            FROM big_activity a
            """;

    private static final RowMapper<BigActivity> ROW_MAPPER = (rs, rowNum) -> new BigActivity(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getInt("act_type"),
            rs.getString("start_time"),
            rs.getString("end_time"),
            rs.getString("audience_scope"),
            rs.getBigDecimal("budget"),
            rs.getString("collection_discount_json"),
            rs.getString("created_by"),
            rs.getString("created_at"),
            rs.getString("updated_by"),
            rs.getString("updated_at"),
            rs.getLong("product_count")
    );

    private final JdbcTemplate jdbcTemplate;

    public BigActivityRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<BigActivity> findPage(String keyword, Integer actType, String audienceScope,
                                      int page, int size) {
        List<Object> args = new ArrayList<>();
        String where = buildWhere(keyword, actType, audienceScope, args);
        args.add(size);
        args.add((page - 1) * size);
        String sql = SELECT_WITH_COUNT + where + " ORDER BY a.id DESC LIMIT ? OFFSET ?";
        return jdbcTemplate.query(sql, ROW_MAPPER, args.toArray());
    }

    public long count(String keyword, Integer actType, String audienceScope) {
        List<Object> args = new ArrayList<>();
        String where = buildWhere(keyword, actType, audienceScope, args);
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM big_activity a " + where, Long.class, args.toArray());
        return total == null ? 0 : total;
    }

    public Optional<BigActivity> findById(long id) {
        List<BigActivity> rows = jdbcTemplate.query(
                SELECT_WITH_COUNT + " WHERE a.id = ?", ROW_MAPPER, id);
        return rows.stream().findFirst();
    }

    public List<BigActivity> findAllOptions() {
        return jdbcTemplate.query(
                "SELECT id, name, act_type, start_time, end_time, audience_scope, budget, "
                        + "collection_discount_json, created_by, created_at, updated_by, updated_at, "
                        + "0 AS product_count FROM big_activity ORDER BY id DESC", ROW_MAPPER);
    }

    public long insert(BigActivity activity) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO big_activity
                        (name, act_type, start_time, end_time, audience_scope, budget,
                         collection_discount_json, created_by, created_at, updated_by, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, activity.name());
            ps.setInt(2, activity.actType());
            ps.setString(3, activity.startTime());
            ps.setString(4, activity.endTime());
            ps.setString(5, activity.audienceScope());
            ps.setBigDecimal(6, activity.budget());
            ps.setString(7, activity.collectionDiscountJson());
            ps.setString(8, activity.createdBy());
            ps.setString(9, activity.createdAt());
            ps.setString(10, activity.updatedBy());
            ps.setString(11, activity.updatedAt());
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("创建大活动失败：未获取到主键");
        }
        return key.longValue();
    }

    public int update(BigActivity activity) {
        return jdbcTemplate.update("""
                        UPDATE big_activity
                        SET name = ?, act_type = ?, start_time = ?, end_time = ?,
                            audience_scope = ?, budget = ?, collection_discount_json = ?,
                            updated_by = ?, updated_at = ?
                        WHERE id = ?
                        """,
                activity.name(), activity.actType(), activity.startTime(), activity.endTime(),
                activity.audienceScope(), activity.budget(), activity.collectionDiscountJson(),
                activity.updatedBy(), activity.updatedAt(), activity.id());
    }

    public int deleteById(long id) {
        return jdbcTemplate.update("DELETE FROM big_activity WHERE id = ?", id);
    }

    public long countAll() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM big_activity", Long.class);
        return count == null ? 0 : count;
    }

    public long countOngoing(String now) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM big_activity WHERE start_time <= ? AND end_time >= ?",
                Long.class, now, now);
        return count == null ? 0 : count;
    }

    public BigDecimal sumBudget() {
        BigDecimal sum = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(budget), 0) FROM big_activity", BigDecimal.class);
        return sum == null ? BigDecimal.ZERO : sum;
    }

    private String buildWhere(String keyword, Integer actType, String audienceScope, List<Object> args) {
        List<String> conditions = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            conditions.add("a.name LIKE ? ESCAPE '\\'");
            args.add("%" + escapeLike(keyword.trim()) + "%");
        }
        if (actType != null) {
            conditions.add("a.act_type = ?");
            args.add(actType);
        }
        if (audienceScope != null && !audienceScope.isBlank()) {
            conditions.add("a.audience_scope = ?");
            args.add(audienceScope.trim());
        }
        return conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
    }

    public static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
