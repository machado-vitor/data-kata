package com.datakata.api.model;

import java.math.BigDecimal;

public record SalesmanRanking(int rank, String salesmanName, String country, BigDecimal totalSales, long transactionCount) {}
