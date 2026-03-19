package com.datakata.flink.model;

public record SalesmanResult(
    String windowStart,
    String windowEnd,
    String salesmanName,
    String country,
    double totalSales,
    long transactionCount,
    int rank
) {}
