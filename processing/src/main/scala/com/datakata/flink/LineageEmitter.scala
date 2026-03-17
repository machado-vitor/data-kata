package com.datakata.flink

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.{ArrayNode, ObjectNode}
import org.slf4j.LoggerFactory

import java.net.{HttpURLConnection, URI}
import java.nio.charset.StandardCharsets
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

object LineageEmitter:

  private val logger = LoggerFactory.getLogger(getClass)
  private val mapper = new ObjectMapper()

  case class Dataset(namespace: String, name: String, schema: List[(String, String)])

  private val salesEventSchema: List[(String, String)] = List(
    ("saleId", "STRING"),
    ("salesmanName", "STRING"),
    ("city", "STRING"),
    ("country", "STRING"),
    ("amount", "DOUBLE"),
    ("product", "STRING"),
    ("eventTime", "LONG"),
    ("source", "STRING"),
    ("ingestionTime", "LONG")
  )

  private val topSalesCitySchema: List[(String, String)] = List(
    ("window_start", "STRING"),
    ("window_end", "STRING"),
    ("city", "STRING"),
    ("total_sales", "DOUBLE"),
    ("transaction_count", "LONG"),
    ("rank", "INTEGER")
  )

  private val topSalesmanCountrySchema: List[(String, String)] = List(
    ("window_start", "STRING"),
    ("window_end", "STRING"),
    ("salesman_name", "STRING"),
    ("country", "STRING"),
    ("total_sales", "DOUBLE"),
    ("transaction_count", "LONG"),
    ("rank", "INTEGER")
  )

  def datasetsForJob(jobName: String): (List[Dataset], List[Dataset]) =
    jobName match
      case "NormalizationJob" =>
        val inputs = List(
          Dataset("kafka", "sales.postgres", salesEventSchema),
          Dataset("kafka", "sales.files", salesEventSchema),
          Dataset("kafka", "sales.legacy", salesEventSchema)
        )
        val outputs = List(
          Dataset("kafka", "sales.unified", salesEventSchema)
        )
        (inputs, outputs)
      case "TopSalesCityJob" =>
        val inputs = List(Dataset("kafka", "sales.unified", salesEventSchema))
        val outputs = List(Dataset("clickhouse", "top_sales_city", topSalesCitySchema))
        (inputs, outputs)
      case "TopSalesmanCountryJob" =>
        val inputs = List(Dataset("kafka", "sales.unified", salesEventSchema))
        val outputs = List(Dataset("clickhouse", "top_salesman_country", topSalesmanCountrySchema))
        (inputs, outputs)
      case _ => (List.empty, List.empty)

  def emit(marquezUrl: String, jobName: String, inputs: List[String], outputs: List[String]): Unit =
    val (inputDatasets, outputDatasets) = datasetsForJob(jobName)
    val runId = UUID.randomUUID().toString
    emitEvent(marquezUrl, jobName, "START", inputDatasets, outputDatasets, runId)
    emitEvent(marquezUrl, jobName, "COMPLETE", inputDatasets, outputDatasets, runId)

  private def buildDatasetNode(ds: Dataset): ObjectNode =
    val node = mapper.createObjectNode()
    node.put("namespace", ds.namespace)
    node.put("name", ds.name)

    val facets = mapper.createObjectNode()
    val schemaFacet = mapper.createObjectNode()
    schemaFacet.put("_producer", "https://github.com/datakata/flink")
    schemaFacet.put("_schemaURL", "https://openlineage.io/spec/facets/1-1-1/SchemaDatasetFacet.json#/$defs/SchemaDatasetFacet")
    val fields = mapper.createArrayNode()
    ds.schema.foreach { case (name, fieldType) =>
      val field = mapper.createObjectNode()
      field.put("name", name)
      field.put("type", fieldType)
      fields.add(field)
    }
    schemaFacet.set("fields", fields)
    facets.set("schema", schemaFacet)

    if ds.namespace == "kafka" then
      val sourceFacet = mapper.createObjectNode()
      sourceFacet.put("_producer", "https://github.com/datakata/flink")
      sourceFacet.put("_schemaURL", "https://openlineage.io/spec/facets/1-0-1/DatasourceDatasetFacet.json#/$defs/DatasourceDatasetFacet")
      sourceFacet.put("name", "kafka")
      sourceFacet.put("uri", "kafka://kafka:9092")
      facets.set("datasource", sourceFacet)
    else if ds.namespace == "clickhouse" then
      val sourceFacet = mapper.createObjectNode()
      sourceFacet.put("_producer", "https://github.com/datakata/flink")
      sourceFacet.put("_schemaURL", "https://openlineage.io/spec/facets/1-0-1/DatasourceDatasetFacet.json#/$defs/DatasourceDatasetFacet")
      sourceFacet.put("name", "clickhouse")
      sourceFacet.put("uri", "clickhouse://clickhouse:8123/datakata")
      facets.set("datasource", sourceFacet)

    node.set("facets", facets)
    node

  private def emitEvent(marquezUrl: String, jobName: String, eventType: String,
                        inputs: List[Dataset], outputs: List[Dataset], runId: String): Unit =
    try
      val event = mapper.createObjectNode()
      event.put("eventType", eventType)
      event.put("eventTime", ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
      event.put("producer", "https://github.com/datakata/flink")
      event.put("schemaURL", "https://openlineage.io/spec/2-0-2/OpenLineage.json#/$defs/RunEvent")

      val run = mapper.createObjectNode()
      run.put("runId", runId)
      event.set("run", run)

      val job = mapper.createObjectNode()
      job.put("namespace", "data-kata")
      job.put("name", jobName)
      event.set("job", job)

      val inputsArray = mapper.createArrayNode()
      inputs.foreach(ds => inputsArray.add(buildDatasetNode(ds)))
      event.set("inputs", inputsArray)

      val outputsArray = mapper.createArrayNode()
      outputs.foreach(ds => outputsArray.add(buildDatasetNode(ds)))
      event.set("outputs", outputsArray)

      val jsonBody = mapper.writeValueAsString(event)
      val url = URI.create(marquezUrl).toURL
      val conn = url.openConnection().asInstanceOf[HttpURLConnection]
      conn.setRequestMethod("POST")
      conn.setRequestProperty("Content-Type", "application/json")
      conn.setDoOutput(true)
      conn.setConnectTimeout(5000)
      conn.setReadTimeout(5000)

      val os = conn.getOutputStream
      os.write(jsonBody.getBytes(StandardCharsets.UTF_8))
      os.flush()
      os.close()

      val responseCode = conn.getResponseCode
      conn.disconnect()

      if responseCode >= 200 && responseCode < 300 then
        logger.info("Emitted OpenLineage {} event for {} (HTTP {})", eventType, jobName, responseCode)
      else
        logger.warn("OpenLineage event for {} returned HTTP {}", jobName, responseCode)
    catch
      case e: Exception =>
        logger.warn("Failed to emit OpenLineage event for {}: {}", jobName, e.getMessage)
