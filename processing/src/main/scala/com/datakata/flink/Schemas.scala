package com.datakata.flink

import com.datakata.flink.LineageEmitter.{Dataset, Field}

object Schemas:

  val salesEvent: List[Field] = List(
    Field("saleId", "STRING"),
    Field("salesmanName", "STRING"),
    Field("city", "STRING"),
    Field("country", "STRING"),
    Field("amount", "DOUBLE"),
    Field("product", "STRING"),
    Field("eventTime", "LONG"),
    Field("source", "STRING"),
    Field("ingestionTime", "LONG")
  )

  val topSalesCity: List[Field] = List(
    Field("window_start", "STRING"),
    Field("window_end", "STRING"),
    Field("city", "STRING"),
    Field("total_sales", "DOUBLE"),
    Field("transaction_count", "LONG"),
    Field("rank", "INTEGER")
  )

  val topSalesmanCountry: List[Field] = List(
    Field("window_start", "STRING"),
    Field("window_end", "STRING"),
    Field("salesman_name", "STRING"),
    Field("country", "STRING"),
    Field("total_sales", "DOUBLE"),
    Field("transaction_count", "LONG"),
    Field("rank", "INTEGER")
  )

  // Pre-built dataset references for lineage
  val kafkaPostgres: Dataset = Dataset("kafka", "sales.postgres", salesEvent)
  val kafkaFiles: Dataset = Dataset("kafka", "sales.files", salesEvent)
  val kafkaLegacy: Dataset = Dataset("kafka", "sales.legacy", salesEvent)
  val kafkaUnified: Dataset = Dataset("kafka", "sales.unified", salesEvent)
  val clickhouseTopCity: Dataset = Dataset("clickhouse", "top_sales_city", topSalesCity)
  val clickhouseTopSalesman: Dataset = Dataset("clickhouse", "top_salesman_country", topSalesmanCountry)
