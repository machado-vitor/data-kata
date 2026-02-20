package com.datakata.api.model

import java.math.BigDecimal
import java.time.LocalDateTime

data class TopSalesman(
    val windowStart: LocalDateTime,
    val windowEnd: LocalDateTime,
    val salesmanName: String,
    val country: String,
    val totalSales: BigDecimal,
    val transactionCount: Long,
    val rank: Int
)

data class TopSalesmanResponse(
    val window: WindowInfo,
    val rankings: List<SalesmanRanking>
)

data class SalesmanRanking(
    val rank: Int,
    val salesmanName: String,
    val country: String,
    val totalSales: BigDecimal,
    val transactionCount: Long
)
