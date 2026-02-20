package com.datakata.api.repository

import com.datakata.api.model.TopSalesman
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet

@Repository
class TopSalesmanRepository(private val jdbcTemplate: JdbcTemplate) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun findLatest(country: String, limit: Int): List<TopSalesman> {
        val sql = """
            SELECT window_start, window_end, salesman_name, country, total_sales, transaction_count, rank
            FROM top_salesman_country FINAL
            WHERE window_start = (SELECT MAX(window_start) FROM top_salesman_country FINAL WHERE country = ?)
              AND country = ?
            ORDER BY rank ASC
            LIMIT ?
        """.trimIndent()

        logger.debug("Querying top salesmen, country={}, limit={}", country, limit)
        return jdbcTemplate.query(sql, { rs, _ -> mapRow(rs) }, country, country, limit)
    }

    fun findByWindow(windowStart: String, country: String, limit: Int): List<TopSalesman> {
        val sql = """
            SELECT window_start, window_end, salesman_name, country, total_sales, transaction_count, rank
            FROM top_salesman_country FINAL
            WHERE window_start = parseDateTimeBestEffort(?)
              AND country = ?
            ORDER BY rank ASC
            LIMIT ?
        """.trimIndent()

        return jdbcTemplate.query(sql, { rs, _ -> mapRow(rs) }, windowStart, country, limit)
    }

    private fun mapRow(rs: ResultSet): TopSalesman {
        return TopSalesman(
            windowStart = rs.getTimestamp("window_start").toLocalDateTime(),
            windowEnd = rs.getTimestamp("window_end").toLocalDateTime(),
            salesmanName = rs.getString("salesman_name"),
            country = rs.getString("country"),
            totalSales = rs.getBigDecimal("total_sales"),
            transactionCount = rs.getLong("transaction_count"),
            rank = rs.getInt("rank")
        )
    }
}
