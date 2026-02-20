package com.datakata.api.model

import java.math.BigDecimal
import java.time.LocalDateTime

data class TopSalesCity(
    val windowStart: LocalDateTime,
    val windowEnd: LocalDateTime,
    val city: String,
    val totalSales: BigDecimal,
    val transactionCount: Long,
    val rank: Int
)

data class TopSalesCityResponse(
    val window: WindowInfo,
    val rankings: List<CityRanking>
)

data class CityRanking(
    val rank: Int,
    val city: String,
    val totalSales: BigDecimal,
    val transactionCount: Long
)

data class WindowInfo(
    val start: String,
    val end: String
)
