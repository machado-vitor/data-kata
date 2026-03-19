package com.datakata.api.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TopSalesCity(
    LocalDateTime windowStart,
    LocalDateTime windowEnd,
    String city,
    BigDecimal totalSales,
    long transactionCount,
    int rank
) {}
