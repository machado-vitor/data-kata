package com.datakata.flink;

import com.datakata.flink.LineageEmitter.Dataset;
import com.datakata.flink.LineageEmitter.Field;

import java.util.List;

public final class Schemas {

    private Schemas() {}

    public static final List<Field> SALES_EVENT = List.of(
        new Field("saleId", "STRING"),
        new Field("salesmanName", "STRING"),
        new Field("city", "STRING"),
        new Field("country", "STRING"),
        new Field("amount", "DOUBLE"),
        new Field("product", "STRING"),
        new Field("eventTime", "LONG"),
        new Field("source", "STRING"),
        new Field("ingestionTime", "LONG")
    );

    public static final List<Field> TOP_SALES_CITY = List.of(
        new Field("window_start", "STRING"),
        new Field("window_end", "STRING"),
        new Field("city", "STRING"),
        new Field("total_sales", "DOUBLE"),
        new Field("transaction_count", "LONG"),
        new Field("rank", "INTEGER")
    );

    public static final List<Field> TOP_SALESMAN_COUNTRY = List.of(
        new Field("window_start", "STRING"),
        new Field("window_end", "STRING"),
        new Field("salesman_name", "STRING"),
        new Field("country", "STRING"),
        new Field("total_sales", "DOUBLE"),
        new Field("transaction_count", "LONG"),
        new Field("rank", "INTEGER")
    );

    public static final Dataset KAFKA_POSTGRES = new Dataset("kafka", "sales.postgres", SALES_EVENT);
    public static final Dataset KAFKA_FILES = new Dataset("kafka", "sales.files", SALES_EVENT);
    public static final Dataset KAFKA_LEGACY = new Dataset("kafka", "sales.legacy", SALES_EVENT);
    public static final Dataset KAFKA_UNIFIED = new Dataset("kafka", "sales.unified", SALES_EVENT);
    public static final Dataset CLICKHOUSE_TOP_CITY = new Dataset("clickhouse", "top_sales_city", TOP_SALES_CITY);
    public static final Dataset CLICKHOUSE_TOP_SALESMAN = new Dataset("clickhouse", "top_salesman_country", TOP_SALESMAN_COUNTRY);
}
