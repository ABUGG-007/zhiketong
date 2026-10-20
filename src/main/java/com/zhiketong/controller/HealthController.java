package com.zhiketong.controller;

import com.zhiketong.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查：应用、MySQL、Redis 状态。
 */
@Tag(name = "00. 健康检查")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HealthController {

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    @Operation(summary = "健康检查")
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> checks = new LinkedHashMap<>();
        boolean mysqlUp = checkMysql(checks);
        boolean redisUp = checkRedis(checks);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("application", "zhiketong-server");
        data.put("version", "1.0.0");
        data.put("status", mysqlUp && redisUp ? "UP" : "DEGRADED");
        data.put("time", LocalDateTime.now());
        data.put("checks", checks);
        return Result.success(data);
    }

    private boolean checkMysql(Map<String, Object> checks) {
        Map<String, Object> mysql = new LinkedHashMap<>();
        try {
            Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            boolean ok = one != null && one == 1;
            mysql.put("status", ok ? "UP" : "DOWN");
            if (ok) {
                String version = jdbcTemplate.queryForObject("SELECT VERSION()", String.class);
                mysql.put("version", version);
            }
            checks.put("mysql", mysql);
            return ok;
        } catch (Exception e) {
            mysql.put("status", "DOWN");
            mysql.put("error", e.getMessage());
            checks.put("mysql", mysql);
            return false;
        }
    }

    private boolean checkRedis(Map<String, Object> checks) {
        Map<String, Object> redis = new LinkedHashMap<>();
        try {
            String pong;
            try (RedisConnection connection = stringRedisTemplate.getConnectionFactory().getConnection()) {
                pong = connection.ping();
            }
            boolean ok = pong != null;
            redis.put("status", ok ? "UP" : "DOWN");
            redis.put("ping", pong);
            checks.put("redis", redis);
            return ok;
        } catch (Exception e) {
            redis.put("status", "DOWN");
            redis.put("error", e.getMessage());
            checks.put("redis", redis);
            return false;
        }
    }
}
