package com.datakata.api.repository

import com.datakata.api.model.TopSalesCity
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet

@Repository
class TopSalesCityRepository(private val jdbcTemplate: JdbcTemplate) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun findLatest(limit: Int): List<TopSalesCity> {
        val sql = """
            SELECT window_start, window_end, city, total_sales, transaction_count, rank
            FROM top_sales_city FINAL
            WHERE window_start = (SELECT MAX(window_start) FROM top_sales_city FINAL)
            ORDER BY rank ASC
            LIMIT ?
        """.trimIndent()

        logger.debug("Querying top sales by city, limit={}", limit)
        return jdbcTemplate.query(sql, { rs, _ -> mapRow(rs) }, limit)
    }

    fun findByWindow(windowStart: String, limit: Int): List<TopSalesCity> {
        val sql = """
            SELECT window_start, window_end, city, total_sales, transaction_count, rank
            FROM top_sales_city FINAL
            WHERE window_start = parseDateTimeBestEffort(?)
            ORDER BY rank ASC
            LIMIT ?
        """.trimIndent()

        return jdbcTemplate.query(sql, { rs, _ -> mapRow(rs) }, windowStart, limit)
    }

    private fun mapRow(rs: ResultSet): TopSalesCity {
        return TopSalesCity(
            windowStart = rs.getTimestamp("window_start").toLocalDateTime(),
            windowEnd = rs.getTimestamp("window_end").toLocalDateTime(),
            city = rs.getString("city"),
            totalSales = rs.getBigDecimal("total_sales"),
            transactionCount = rs.getLong("transaction_count"),
            rank = rs.getInt("rank")
        )
    }
}
