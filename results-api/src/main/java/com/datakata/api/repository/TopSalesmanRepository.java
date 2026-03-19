package com.datakata.api.repository;

import com.datakata.api.model.TopSalesman;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class TopSalesmanRepository {

    private static final Logger logger = LoggerFactory.getLogger(TopSalesmanRepository.class);
    private final JdbcTemplate jdbcTemplate;

    public TopSalesmanRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<TopSalesman> findLatest(String country, int limit) {
        var sql = """
            SELECT window_start, window_end, salesman_name, country, total_sales, transaction_count, rank
            FROM top_salesman_country FINAL
            WHERE window_start = (SELECT MAX(window_start) FROM top_salesman_country FINAL WHERE country = ?)
              AND country = ?
            ORDER BY rank ASC
            LIMIT ?""";
        logger.debug("Querying top salesmen, country={}, limit={}", country, limit);
        return jdbcTemplate.query(sql, (rs, rowNum) -> mapRow(rs), country, country, limit);
    }

    public List<TopSalesman> findByWindow(String windowStart, String country, int limit) {
        var sql = """
            SELECT window_start, window_end, salesman_name, country, total_sales, transaction_count, rank
            FROM top_salesman_country FINAL
            WHERE window_start = parseDateTimeBestEffort(?)
              AND country = ?
            ORDER BY rank ASC
            LIMIT ?""";
        return jdbcTemplate.query(sql, (rs, rowNum) -> mapRow(rs), windowStart, country, limit);
    }

    private TopSalesman mapRow(ResultSet rs) throws SQLException {
        return new TopSalesman(
            rs.getTimestamp("window_start").toLocalDateTime(),
            rs.getTimestamp("window_end").toLocalDateTime(),
            rs.getString("salesman_name"),
            rs.getString("country"),
            rs.getBigDecimal("total_sales"),
            rs.getLong("transaction_count"),
            rs.getInt("rank")
        );
    }
}
