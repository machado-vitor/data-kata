package com.datakata.flink.sink

import org.apache.flink.connector.jdbc.{JdbcConnectionOptions, JdbcExecutionOptions, JdbcSink, JdbcStatementBuilder}

import java.sql.PreparedStatement

object ClickHouseSink:

  private val clickhouseUrl: String =
    sys.env.getOrElse("CLICKHOUSE_URL", "jdbc:ch://clickhouse:8123/datakata")
  private val clickhouseUser: String =
    sys.env.getOrElse("CLICKHOUSE_USER", "datakata")
  private val clickhousePassword: String =
    sys.env.getOrElse("CLICKHOUSE_PASSWORD", "datakata")

  private def connectionOptions: JdbcConnectionOptions =
    new JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
      .withUrl(clickhouseUrl)
      .withDriverName("com.clickhouse.jdbc.ClickHouseDriver")
      .withUsername(clickhouseUser)
      .withPassword(clickhousePassword)
      .build()

  private def executionOptions: JdbcExecutionOptions =
    JdbcExecutionOptions.builder()
      .withBatchSize(100)
      .withBatchIntervalMs(5000)
      .withMaxRetries(3)
      .build()

  def topSalesCitySink: org.apache.flink.streaming.api.functions.sink.SinkFunction[(String, String, String, Double, Long, Int)] =
    JdbcSink.sink(
      """INSERT INTO top_sales_city (window_start, window_end, city, total_sales, transaction_count, rank)
        |VALUES (?, ?, ?, ?, ?, ?)""".stripMargin,
      new JdbcStatementBuilder[(String, String, String, Double, Long, Int)]:
        override def accept(ps: PreparedStatement, t: (String, String, String, Double, Long, Int)): Unit =
          ps.setString(1, t._1)
          ps.setString(2, t._2)
          ps.setString(3, t._3)
          ps.setDouble(4, t._4)
          ps.setLong(5, t._5)
          ps.setInt(6, t._6)
      ,
      executionOptions,
      connectionOptions
    )

  def topSalesmanCountrySink: org.apache.flink.streaming.api.functions.sink.SinkFunction[(String, String, String, String, Double, Long, Int)] =
    JdbcSink.sink(
      """INSERT INTO top_salesman_country (window_start, window_end, salesman_name, country, total_sales, transaction_count, rank)
        |VALUES (?, ?, ?, ?, ?, ?, ?)""".stripMargin,
      new JdbcStatementBuilder[(String, String, String, String, Double, Long, Int)]:
        override def accept(ps: PreparedStatement, t: (String, String, String, String, Double, Long, Int)): Unit =
          ps.setString(1, t._1)
          ps.setString(2, t._2)
          ps.setString(3, t._3)
          ps.setString(4, t._4)
          ps.setDouble(5, t._5)
          ps.setLong(6, t._6)
          ps.setInt(7, t._7)
      ,
      executionOptions,
      connectionOptions
    )
