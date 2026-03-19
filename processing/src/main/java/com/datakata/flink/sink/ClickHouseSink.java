package com.datakata.flink.sink;

import com.datakata.flink.model.CityResult;
import com.datakata.flink.model.SalesmanResult;
import org.apache.flink.api.connector.sink2.Sink;
import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.JdbcStatementBuilder;
import org.apache.flink.connector.jdbc.core.datastream.sink.JdbcSink;

public final class ClickHouseSink {

    private static final String CLICKHOUSE_URL =
        System.getenv().getOrDefault("CLICKHOUSE_URL", "jdbc:ch://clickhouse:8123/datakata");
    private static final String CLICKHOUSE_USER =
        System.getenv().getOrDefault("CLICKHOUSE_USER", "datakata");
    private static final String CLICKHOUSE_PASSWORD =
        System.getenv().getOrDefault("CLICKHOUSE_PASSWORD", "datakata");

    private ClickHouseSink() {}

    private static JdbcConnectionOptions connectionOptions() {
        return new JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
            .withUrl(CLICKHOUSE_URL)
            .withDriverName("com.clickhouse.jdbc.ClickHouseDriver")
            .withUsername(CLICKHOUSE_USER)
            .withPassword(CLICKHOUSE_PASSWORD)
            .build();
    }

    private static JdbcExecutionOptions executionOptions() {
        return JdbcExecutionOptions.builder()
            .withBatchSize(100)
            .withBatchIntervalMs(5000)
            .withMaxRetries(3)
            .build();
    }

    public static Sink<CityResult> topSalesCitySink() {
        return JdbcSink.<CityResult>builder()
            .withQueryStatement(
                """
                INSERT INTO top_sales_city (window_start, window_end, city, total_sales, transaction_count, rank)
                VALUES (?, ?, ?, ?, ?, ?)""",
                (JdbcStatementBuilder<CityResult>) (ps, t) -> {
                    ps.setString(1, t.windowStart());
                    ps.setString(2, t.windowEnd());
                    ps.setString(3, t.city());
                    ps.setDouble(4, t.totalSales());
                    ps.setLong(5, t.transactionCount());
                    ps.setInt(6, t.rank());
                })
            .withExecutionOptions(executionOptions())
            .buildAtLeastOnce(connectionOptions());
    }

    public static Sink<SalesmanResult> topSalesmanCountrySink() {
        return JdbcSink.<SalesmanResult>builder()
            .withQueryStatement(
                """
                INSERT INTO top_salesman_country (window_start, window_end, salesman_name, country, total_sales, transaction_count, rank)
                VALUES (?, ?, ?, ?, ?, ?, ?)""",
                (JdbcStatementBuilder<SalesmanResult>) (ps, t) -> {
                    ps.setString(1, t.windowStart());
                    ps.setString(2, t.windowEnd());
                    ps.setString(3, t.salesmanName());
                    ps.setString(4, t.country());
                    ps.setDouble(5, t.totalSales());
                    ps.setLong(6, t.transactionCount());
                    ps.setInt(7, t.rank());
                })
            .withExecutionOptions(executionOptions())
            .buildAtLeastOnce(connectionOptions());
    }
}
