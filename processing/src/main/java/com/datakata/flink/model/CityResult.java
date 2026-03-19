package com.datakata.flink.model;

public record CityResult(
    String windowStart,
    String windowEnd,
    String city,
    double totalSales,
    long transactionCount,
    int rank
) {}
