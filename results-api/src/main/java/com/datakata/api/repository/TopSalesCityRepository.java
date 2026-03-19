package com.datakata.api.repository;

import com.datakata.api.model.TopSalesCity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class TopSalesCityRepository {

    private static final Logger logger = LoggerFactory.getLogger(TopSalesCityRepository.class);
    private final JdbcTemplate jdbcTemplate;

    public TopSalesCityRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<TopSalesCity> findLatest(int limit) {
        var sql = """
            SELECT window_start, window_end, city, total_sales, transaction_count, rank
            FROM top_sales_city FINAL
            WHERE window_start = (SELECT MAX(window_start) FROM top_sales_city FINAL)
            ORDER BY rank ASC
            LIMIT ?""";
        logger.debug("Querying top sales by city, limit={}", limit);
        return jdbcTemplate.query(sql, (rs, rowNum) -> mapRow(rs), limit);
    }

    public List<TopSalesCity> findByWindow(String windowStart, int limit) {
        var sql = """
            SELECT window_start, window_end, city, total_sales, transaction_count, rank
            FROM top_sales_city FINAL
            WHERE window_start = parseDateTimeBestEffort(?)
            ORDER BY rank ASC
            LIMIT ?""";
        return jdbcTemplate.query(sql, (rs, rowNum) -> mapRow(rs), windowStart, limit);
    }

    private TopSalesCity mapRow(ResultSet rs) throws SQLException {
        return new TopSalesCity(
            rs.getTimestamp("window_start").toLocalDateTime(),
            rs.getTimestamp("window_end").toLocalDateTime(),
            rs.getString("city"),
            rs.getBigDecimal("total_sales"),
            rs.getLong("transaction_count"),
            rs.getInt("rank")
        );
    }
}
