package com.datakata.flink.model

case class SalesEvent(
    saleId: String,
    salesmanName: String,
    city: String,
    country: String,
    amount: Double,
    product: String,
    eventTime: Long,
    source: String,
    ingestionTime: Long
)
