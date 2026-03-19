package com.datakata.api.model;

import java.math.BigDecimal;

public record CityRanking(int rank, String city, BigDecimal totalSales, long transactionCount) {}
