package com.datakata.producer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class PostgresClient {

    private static final Logger log = LoggerFactory.getLogger(PostgresClient.class);
    private static final String PRODUCER_ID = "pg-producer";

    @Value("${postgres.url}")
    private String url;

    @Value("${postgres.username}")
    private String username;

    @Value("${postgres.password}")
    private String password;

    private Connection connection;

    @PostConstruct
    public void init() {
        connect();
    }

    private void connect() {
        try {
            if (connection != null && !connection.isClosed()) return;
            connection = DriverManager.getConnection(url, username, password);
            log.info("Connected to PostgreSQL at {}", url);
        } catch (SQLException e) {
            log.error("Failed to connect to PostgreSQL", e);
        }
    }

    public long loadOffset() {
        String sql = "SELECT offset_value FROM producer_offsets WHERE producer_id = ?";
        try {
            connect();
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, PRODUCER_ID);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        long offset = rs.getLong("offset_value");
                        log.info("Loaded persisted offset: lastMaxId={}", offset);
                        return offset;
                    }
                }
            }
        } catch (SQLException e) {
            log.warn("Failed to load offset from producer_offsets, starting from 0", e);
        }
        return 0;
    }

    public void saveOffset(long lastMaxId) {
        String sql = """
            INSERT INTO producer_offsets (producer_id, offset_value, updated_at)
            VALUES (?, ?, NOW())
            ON CONFLICT (producer_id) DO UPDATE SET offset_value = EXCLUDED.offset_value, updated_at = NOW()""";
        try {
            connect();
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, PRODUCER_ID);
                stmt.setLong(2, lastMaxId);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            log.error("Failed to save offset to producer_offsets", e);
        }
    }

    public List<Map<String, Object>> fetchNewSales(long lastMaxId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        String sql = "SELECT id, salesman_id, salesman, city, country, amount, product, sale_date, created_at " +
                     "FROM sales WHERE id > ? ORDER BY id LIMIT 10000";

        try {
            connect();
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setLong(1, lastMaxId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new HashMap<>();
                        row.put("id", rs.getLong("id"));
                        row.put("salesman_id", rs.getLong("salesman_id"));
                        row.put("salesman", rs.getString("salesman"));
                        row.put("city", rs.getString("city"));
                        row.put("country", rs.getString("country"));
                        row.put("amount", rs.getBigDecimal("amount"));
                        row.put("product", rs.getString("product"));
                        Timestamp saleDate = rs.getTimestamp("sale_date");
                        row.put("sale_date", saleDate != null ? saleDate.toInstant().toString() : null);
                        Timestamp createdAt = rs.getTimestamp("created_at");
                        row.put("created_at", createdAt != null ? createdAt.toInstant().toString() : null);
                        rows.add(row);
                    }
                }
            }
        } catch (SQLException e) {
            log.error("Failed to query sales table", e);
            try { connection.close(); } catch (Exception ignored) {}
            connection = null;
        }

        return rows;
    }

    @PreDestroy
    public void shutdown() {
        if (connection != null) {
            try {
                connection.close();
                log.info("PostgreSQL connection closed.");
            } catch (SQLException e) {
                log.error("Error closing PostgreSQL connection", e);
            }
        }
    }
}
