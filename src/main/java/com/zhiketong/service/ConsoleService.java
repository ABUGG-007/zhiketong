package com.zhiketong.service;

import com.zhiketong.common.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 控制台只读查询服务。
 *
 * <p>看板与列表页的只读过渡接口，直接查询本系统 MySQL 业务表；
 * 不构造、不返回任何模拟数据。正式模块接口按 M1～M6 里程碑逐步替换。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsoleService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 看板概览：指标、评分、7 日趋势、最近工单/会话、坐席状态。
     */
    public Map<String, Object> overview() {
        Map<String, Object> data = new LinkedHashMap<>();

        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("users", count("SELECT COUNT(*) FROM sys_user WHERE deleted = 0"));
        counts.put("knowledge", count("SELECT COUNT(*) FROM kb_entry WHERE deleted = 0"));
        counts.put("categories", count("SELECT COUNT(*) FROM kb_category WHERE deleted = 0"));
        counts.put("sessionsToday", count(
                "SELECT COUNT(*) FROM chat_session WHERE deleted = 0 AND DATE(create_time) = CURDATE()"));
        counts.put("messagesToday", count(
                "SELECT COUNT(*) FROM chat_message WHERE DATE(create_time) = CURDATE()"));
        counts.put("ordersToday", count(
                "SELECT COUNT(*) FROM work_order WHERE deleted = 0 AND DATE(create_time) = CURDATE()"));
        counts.put("pendingOrders", count(
                "SELECT COUNT(*) FROM work_order WHERE deleted = 0 "
                        + "AND status IN ('PENDING','ASSIGNED','PROCESSING','REOPENED')"));
        counts.put("waitingSessions", count(
                "SELECT COUNT(*) FROM chat_session WHERE deleted = 0 AND status = 'WAITING'"));
        counts.put("overdueOrders", count(
                "SELECT COUNT(*) FROM work_order WHERE deleted = 0 AND overdue_flag = 1 "
                        + "AND status <> 'CLOSED'"));
        counts.put("onlineAgents", count(
                "SELECT COUNT(*) FROM agent_status WHERE online_status = 'ONLINE'"));
        data.put("counts", counts);

        Map<String, Object> ratings = new LinkedHashMap<>();
        ratings.put("avgSessionRating", avgRating(
                "SELECT AVG(rating) FROM chat_session WHERE deleted = 0 AND rating IS NOT NULL"));
        ratings.put("avgOrderRating", avgRating(
                "SELECT AVG(rating) FROM work_order WHERE deleted = 0 AND rating IS NOT NULL"));
        data.put("ratings", ratings);

        data.put("trend", trend());
        data.put("recentOrders", recentOrders());
        data.put("recentSessions", recentSessions());
        data.put("agents", agents());
        return data;
    }

    /**
     * 知识库条目分页。
     */
    public PageResult<Map<String, Object>> kbEntries(String keyword, Long categoryId,
                                                     int pageNum, int pageSize) {
        StringBuilder where = new StringBuilder(" WHERE e.deleted = 0");
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(keyword)) {
            String like = "%" + keyword.trim() + "%";
            where.append(" AND (e.question LIKE ? OR e.answer LIKE ? OR e.keywords LIKE ?)");
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (categoryId != null && categoryId > 0) {
            where.append(" AND e.category_id = ?");
            args.add(categoryId);
        }

        long total = queryCount("SELECT COUNT(*) FROM kb_entry e" + where, args.toArray());
        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add(pageSize);
        listArgs.add(offset(pageNum, pageSize));

        String sql = "SELECT e.id, e.category_id AS categoryId, c.name AS categoryName, "
                + "e.question, e.answer, e.keywords, e.hit_count AS hitCount, e.status, "
                + "e.update_time AS updateTime "
                + "FROM kb_entry e LEFT JOIN kb_category c ON e.category_id = c.id"
                + where + " ORDER BY e.hit_count DESC, e.id DESC LIMIT ? OFFSET ?";
        List<Map<String, Object>> list = jdbcTemplate.query(sql, listArgs.toArray(),
                (rs, rowNum) -> mapRow(rs));
        return page(list, total, pageNum, pageSize);
    }

    /**
     * 知识库分类（含条目数）。
     */
    public List<Map<String, Object>> categories() {
        String sql = "SELECT c.id, c.name, c.parent_id AS parentId, c.sort, c.status, "
                + "COUNT(e.id) AS entryCount "
                + "FROM kb_category c LEFT JOIN kb_entry e "
                + "ON e.category_id = c.id AND e.deleted = 0 "
                + "WHERE c.deleted = 0 "
                + "GROUP BY c.id, c.name, c.parent_id, c.sort, c.status "
                + "ORDER BY c.sort ASC, c.id ASC";
        return jdbcTemplate.query(sql, (rs, rowNum) -> mapRow(rs));
    }

    /**
     * 工单分页。
     */
    public PageResult<Map<String, Object>> workOrders(String status, String priority,
                                                      String keyword, int pageNum, int pageSize) {
        StringBuilder where = new StringBuilder(" WHERE o.deleted = 0");
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(status)) {
            where.append(" AND o.status = ?");
            args.add(status);
        }
        if (StringUtils.hasText(priority)) {
            where.append(" AND o.priority = ?");
            args.add(priority);
        }
        if (StringUtils.hasText(keyword)) {
            String like = "%" + keyword.trim() + "%";
            where.append(" AND (o.title LIKE ? OR o.order_no LIKE ?)");
            args.add(like);
            args.add(like);
        }

        long total = queryCount("SELECT COUNT(*) FROM work_order o" + where, args.toArray());
        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add(pageSize);
        listArgs.add(offset(pageNum, pageSize));

        String sql = "SELECT o.id, o.order_no AS orderNo, o.title, o.category, o.priority, "
                + "o.status, o.source, c.nickname AS creatorName, a.nickname AS assigneeName, "
                + "o.sla_deadline AS slaDeadline, o.overdue_flag AS overdueFlag, "
                + "o.create_time AS createTime "
                + "FROM work_order o "
                + "LEFT JOIN sys_user c ON o.creator_id = c.id "
                + "LEFT JOIN sys_user a ON o.assignee_id = a.id"
                + where + " ORDER BY o.create_time DESC LIMIT ? OFFSET ?";
        List<Map<String, Object>> list = jdbcTemplate.query(sql, listArgs.toArray(),
                (rs, rowNum) -> mapRow(rs));
        return page(list, total, pageNum, pageSize);
    }

    /**
     * 会话分页。
     */
    public PageResult<Map<String, Object>> sessions(String status, int pageNum, int pageSize) {
        StringBuilder where = new StringBuilder(" WHERE s.deleted = 0");
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(status)) {
            where.append(" AND s.status = ?");
            args.add(status);
        }

        long total = queryCount("SELECT COUNT(*) FROM chat_session s" + where, args.toArray());
        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add(pageSize);
        listArgs.add(offset(pageNum, pageSize));

        String sql = "SELECT s.id, s.session_no AS sessionNo, u.nickname AS userName, "
                + "a.nickname AS agentName, s.status, s.rating, "
                + "s.message_count AS messageCount, s.queue_enter_time AS queueEnterTime, "
                + "s.accept_time AS acceptTime, s.end_time AS endTime, s.create_time AS createTime "
                + "FROM chat_session s "
                + "LEFT JOIN sys_user u ON s.user_id = u.id "
                + "LEFT JOIN sys_user a ON s.agent_id = a.id"
                + where + " ORDER BY s.create_time DESC LIMIT ? OFFSET ?";
        List<Map<String, Object>> list = jdbcTemplate.query(sql, listArgs.toArray(),
                (rs, rowNum) -> mapRow(rs));
        return page(list, total, pageNum, pageSize);
    }

    /**
     * 用户分页（不含密码）。
     */
    public PageResult<Map<String, Object>> users(String roleCode, int pageNum, int pageSize) {
        StringBuilder where = new StringBuilder(" WHERE u.deleted = 0");
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(roleCode)) {
            where.append(" AND u.role_code = ?");
            args.add(roleCode);
        }

        long total = queryCount("SELECT COUNT(*) FROM sys_user u" + where, args.toArray());
        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add(pageSize);
        listArgs.add(offset(pageNum, pageSize));

        String sql = "SELECT u.id, u.username, u.nickname, u.role_code AS roleCode, "
                + "CASE u.role_code WHEN 'ADMIN' THEN '管理员' "
                + "WHEN 'AGENT' THEN '客服坐席' ELSE '普通用户' END AS roleName, "
                + "u.phone, u.email, u.status, u.last_login_time AS lastLoginTime, "
                + "u.create_time AS createTime "
                + "FROM sys_user u" + where + " ORDER BY u.id ASC LIMIT ? OFFSET ?";
        List<Map<String, Object>> list = jdbcTemplate.query(sql, listArgs.toArray(),
                (rs, rowNum) -> mapRow(rs));
        return page(list, total, pageNum, pageSize);
    }

    /**
     * 坐席状态列表。
     */
    public List<Map<String, Object>> agents() {
        String sql = "SELECT s.agent_id AS agentId, u.nickname, s.online_status AS onlineStatus, "
                + "s.current_count AS currentCount, s.max_concurrent AS maxConcurrent, "
                + "s.total_served AS totalServed, s.last_heartbeat AS lastHeartbeat "
                + "FROM agent_status s JOIN sys_user u ON s.agent_id = u.id "
                + "ORDER BY FIELD(s.online_status, 'ONLINE', 'BUSY', 'OFFLINE'), s.agent_id ASC";
        return jdbcTemplate.query(sql, (rs, rowNum) -> mapRow(rs));
    }

    /**
     * 等待接入队列。
     */
    public List<Map<String, Object>> waitingSessions() {
        String sql = "SELECT s.id, s.session_no AS sessionNo, u.nickname AS userName, "
                + "s.queue_enter_time AS queueEnterTime, "
                + "TIMESTAMPDIFF(SECOND, COALESCE(s.queue_enter_time, s.transfer_time, s.create_time), NOW()) "
                + "AS waitSeconds "
                + "FROM chat_session s JOIN sys_user u ON s.user_id = u.id "
                + "WHERE s.deleted = 0 AND s.status = 'WAITING' ORDER BY s.queue_enter_time ASC";
        return jdbcTemplate.query(sql, (rs, rowNum) -> mapRow(rs));
    }

    // ------------------------------------------------------------------
    // 私有方法
    // ------------------------------------------------------------------

    private List<Map<String, Object>> recentOrders() {
        String sql = "SELECT o.id, o.order_no AS orderNo, o.title, o.priority, o.status, "
                + "o.category, c.nickname AS creatorName, a.nickname AS assigneeName, "
                + "o.create_time AS createTime "
                + "FROM work_order o "
                + "LEFT JOIN sys_user c ON o.creator_id = c.id "
                + "LEFT JOIN sys_user a ON o.assignee_id = a.id "
                + "WHERE o.deleted = 0 ORDER BY o.create_time DESC LIMIT 5";
        return jdbcTemplate.query(sql, (rs, rowNum) -> mapRow(rs));
    }

    private List<Map<String, Object>> recentSessions() {
        String sql = "SELECT s.id, s.session_no AS sessionNo, u.nickname AS userName, "
                + "a.nickname AS agentName, s.status, s.rating, "
                + "s.message_count AS messageCount, s.create_time AS createTime "
                + "FROM chat_session s "
                + "LEFT JOIN sys_user u ON s.user_id = u.id "
                + "LEFT JOIN sys_user a ON s.agent_id = a.id "
                + "WHERE s.deleted = 0 ORDER BY s.create_time DESC LIMIT 5";
        return jdbcTemplate.query(sql, (rs, rowNum) -> mapRow(rs));
    }

    /**
     * 近 7 日会话/工单趋势（缺数据的日期补 0）。
     */
    private List<Map<String, Object>> trend() {
        Map<String, Integer> sessionMap = countByDay(
                "SELECT DATE(create_time) AS d, COUNT(*) AS c FROM chat_session "
                        + "WHERE deleted = 0 AND create_time >= DATE_SUB(CURDATE(), INTERVAL 6 DAY) "
                        + "GROUP BY DATE(create_time)");
        Map<String, Integer> orderMap = countByDay(
                "SELECT DATE(create_time) AS d, COUNT(*) AS c FROM work_order "
                        + "WHERE deleted = 0 AND create_time >= DATE_SUB(CURDATE(), INTERVAL 6 DAY) "
                        + "GROUP BY DATE(create_time)");

        List<Map<String, Object>> result = new ArrayList<>(7);
        for (int i = 6; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            String key = date.toString();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", key);
            item.put("sessions", sessionMap.getOrDefault(key, 0));
            item.put("orders", orderMap.getOrDefault(key, 0));
            result.add(item);
        }
        return result;
    }

    private Map<String, Integer> countByDay(String sql) {
        Map<String, Integer> map = new HashMap<>();
        jdbcTemplate.query(sql, rs -> {
            java.sql.Date date = rs.getDate("d");
            if (date != null) {
                map.put(date.toLocalDate().toString(), rs.getInt("c"));
            }
        });
        return map;
    }

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }

    private long queryCount(String sql, Object[] args) {
        Long value = jdbcTemplate.queryForObject(sql, args, Long.class);
        return value == null ? 0L : value;
    }

    private double avgRating(String sql) {
        Double value = jdbcTemplate.queryForObject(sql, Double.class);
        if (value == null) {
            return 0D;
        }
        return Math.round(value * 100D) / 100D;
    }

    private long offset(int pageNum, int pageSize) {
        return (long) (pageNum - 1) * pageSize;
    }

    private <T> PageResult<T> page(List<T> list, long total, int pageNum, int pageSize) {
        PageResult<T> page = new PageResult<>();
        page.setList(list);
        page.setTotal(total);
        page.setPageNum(pageNum);
        page.setPageSize(pageSize);
        page.setPages((total + pageSize - 1) / pageSize);
        return page;
    }

    /**
     * 通用行映射：列别名（camelCase）作为 key，Timestamp 转 LocalDateTime。
     */
    private Map<String, Object> mapRow(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();
        Map<String, Object> row = new LinkedHashMap<>(columnCount * 2);
        for (int i = 1; i <= columnCount; i++) {
            String label = meta.getColumnLabel(i);
            Object value = rs.getObject(i);
            if (value instanceof java.sql.Timestamp) {
                value = ((java.sql.Timestamp) value).toLocalDateTime();
            } else if (value instanceof java.sql.Date) {
                value = ((java.sql.Date) value).toLocalDate();
            }
            row.put(label, value);
        }
        return row;
    }
}
