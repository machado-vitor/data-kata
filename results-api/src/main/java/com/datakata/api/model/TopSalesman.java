package com.datakata.api.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TopSalesman(
    LocalDateTime windowStart,
    LocalDateTime windowEnd,
    String salesmanName,
    String country,
    BigDecimal totalSales,
    long transactionCount,
    int rank
) {}
