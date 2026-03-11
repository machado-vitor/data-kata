-- ClickHouse init: Result tables for Data Kata

CREATE DATABASE IF NOT EXISTS datakata;

CREATE TABLE IF NOT EXISTS datakata.top_sales_city (
    window_start DateTime,
    window_end   DateTime,
    city         String,
    total_sales  Decimal(18, 2),
    transaction_count UInt32,
    rank         UInt8,
    updated_at   DateTime DEFAULT now()
) ENGINE = ReplacingMergeTree(updated_at)
ORDER BY (window_start, rank);

CREATE TABLE IF NOT EXISTS datakata.top_salesman_country (
    window_start    DateTime,
    window_end      DateTime,
    salesman_name   String,
    country         String,
    total_sales     Decimal(18, 2),
    transaction_count UInt32,
    rank            UInt8,
    updated_at      DateTime DEFAULT now()
) ENGINE = ReplacingMergeTree(updated_at)
ORDER BY (window_start, rank);
