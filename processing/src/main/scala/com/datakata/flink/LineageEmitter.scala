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

  def emit(marquezUrl: String, jobName: String, inputs: List[String], outputs: List[String]): Unit =
    try
      val event = mapper.createObjectNode()
      event.put("eventType", "START")
      event.put("eventTime", ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
      event.put("producer", "https://github.com/datakata/flink")
      event.put("schemaURL", "https://openlineage.io/spec/2-0-2/OpenLineage.json#/$defs/RunEvent")

      val run = mapper.createObjectNode()
      run.put("runId", UUID.randomUUID().toString)
      event.set("run", run)

      val job = mapper.createObjectNode()
      job.put("namespace", "data-kata")
      job.put("name", jobName)
      event.set("job", job)

      val inputsArray = mapper.createArrayNode()
      inputs.foreach { topic =>
        val ds = mapper.createObjectNode()
        ds.put("namespace", "kafka")
        ds.put("name", topic)
        inputsArray.add(ds)
      }
      event.set("inputs", inputsArray)

      val outputsArray = mapper.createArrayNode()
      outputs.foreach { topic =>
        val ds = mapper.createObjectNode()
        ds.put("namespace", "kafka")
        ds.put("name", topic)
        outputsArray.add(ds)
      }
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
        logger.info("Emitted OpenLineage START event for {} (HTTP {})", jobName, responseCode)
      else
        logger.warn("OpenLineage event for {} returned HTTP {}", jobName, responseCode)
    catch
      case e: Exception =>
        logger.warn("Failed to emit OpenLineage event for {}: {}", jobName, e.getMessage)
