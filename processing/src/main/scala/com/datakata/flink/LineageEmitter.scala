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

  private val PRODUCER = "https://github.com/datakata/flink"
  private val SCHEMA_FACET_URL = "https://openlineage.io/spec/facets/1-1-1/SchemaDatasetFacet.json#/$defs/SchemaDatasetFacet"
  private val DATASOURCE_FACET_URL = "https://openlineage.io/spec/facets/1-0-1/DatasourceDatasetFacet.json#/$defs/DatasourceDatasetFacet"
  private val RUN_EVENT_SCHEMA = "https://openlineage.io/spec/2-0-2/OpenLineage.json#/$defs/RunEvent"
  private val JOB_NAMESPACE = "data-kata"

  case class Field(name: String, fieldType: String)
  case class Dataset(namespace: String, name: String, fields: List[Field])

  private val datasources: Map[String, String] = Map(
    "kafka" -> "kafka://kafka:9092",
    "clickhouse" -> "clickhouse://clickhouse:8123/datakata"
  )

  def emit(marquezUrl: String, jobName: String, inputs: List[Dataset], outputs: List[Dataset]): Unit =
    val runId = UUID.randomUUID().toString
    emitEvent(marquezUrl, jobName, "START", inputs, outputs, runId)
    emitEvent(marquezUrl, jobName, "COMPLETE", inputs, outputs, runId)

  private def buildFieldsArray(fields: List[Field]): ArrayNode =
    val arr = mapper.createArrayNode()
    fields.foreach { f =>
      val node = mapper.createObjectNode()
      node.put("name", f.name)
      node.put("type", f.fieldType)
      arr.add(node)
    }
    arr

  private def buildDatasetNode(ds: Dataset): ObjectNode =
    val node = mapper.createObjectNode()
    node.put("namespace", ds.namespace)
    node.put("name", ds.name)

    val facets = mapper.createObjectNode()

    val schemaFacet = mapper.createObjectNode()
    schemaFacet.put("_producer", PRODUCER)
    schemaFacet.put("_schemaURL", SCHEMA_FACET_URL)
    schemaFacet.set("fields", buildFieldsArray(ds.fields))
    facets.set("schema", schemaFacet)

    datasources.get(ds.namespace).foreach { uri =>
      val sourceFacet = mapper.createObjectNode()
      sourceFacet.put("_producer", PRODUCER)
      sourceFacet.put("_schemaURL", DATASOURCE_FACET_URL)
      sourceFacet.put("name", ds.namespace)
      sourceFacet.put("uri", uri)
      facets.set("datasource", sourceFacet)
    }

    node.set("facets", facets)
    node

  private def buildDatasetsArray(datasets: List[Dataset]): ArrayNode =
    val arr = mapper.createArrayNode()
    datasets.foreach(ds => arr.add(buildDatasetNode(ds)))
    arr

  private def emitEvent(marquezUrl: String, jobName: String, eventType: String,
                        inputs: List[Dataset], outputs: List[Dataset], runId: String): Unit =
    try
      val event = mapper.createObjectNode()
      event.put("eventType", eventType)
      event.put("eventTime", ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
      event.put("producer", PRODUCER)
      event.put("schemaURL", RUN_EVENT_SCHEMA)

      val run = mapper.createObjectNode()
      run.put("runId", runId)
      event.set("run", run)

      val job = mapper.createObjectNode()
      job.put("namespace", JOB_NAMESPACE)
      job.put("name", jobName)
      event.set("job", job)

      event.set("inputs", buildDatasetsArray(inputs))
      event.set("outputs", buildDatasetsArray(outputs))

      post(marquezUrl, mapper.writeValueAsString(event), eventType, jobName)
    catch
      case e: Exception =>
        logger.warn("Failed to emit OpenLineage {} event for {}: {}", eventType, jobName, e.getMessage)

  private def post(url: String, body: String, eventType: String, jobName: String): Unit =
    val conn = URI.create(url).toURL.openConnection().asInstanceOf[HttpURLConnection]
    try
      conn.setRequestMethod("POST")
      conn.setRequestProperty("Content-Type", "application/json")
      conn.setDoOutput(true)
      conn.setConnectTimeout(5000)
      conn.setReadTimeout(5000)

      val os = conn.getOutputStream
      try
        os.write(body.getBytes(StandardCharsets.UTF_8))
        os.flush()
      finally
        os.close()

      val responseCode = conn.getResponseCode
      if responseCode >= 200 && responseCode < 300 then
        logger.info("Emitted OpenLineage {} event for {} (HTTP {})", eventType, jobName, responseCode)
      else
        logger.warn("OpenLineage {} event for {} returned HTTP {}", eventType, jobName, responseCode)
    finally
      conn.disconnect()
