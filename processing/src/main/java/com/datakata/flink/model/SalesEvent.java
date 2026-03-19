package com.datakata.flink.model;

public record SalesEvent(
    String saleId,
    String salesmanName,
    String city,
    String country,
    double amount,
    String product,
    long eventTime,
    String source,
    long ingestionTime
) {
    public SalesEvent withSource(String source) {
        return new SalesEvent(saleId, salesmanName, city, country, amount, product, eventTime, source, ingestionTime);
    }
}
