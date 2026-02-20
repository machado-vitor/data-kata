package com.datakata.flink.serde

import com.datakata.flink.model.SalesEvent
import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import org.apache.flink.api.common.serialization.{SerializationSchema, DeserializationSchema}
import org.apache.flink.api.common.typeinfo.{TypeInformation, TypeHint}
import org.slf4j.LoggerFactory

import java.nio.charset.StandardCharsets

class SalesEventDeserializationSchema extends DeserializationSchema[SalesEvent]:
  @transient private lazy val logger = LoggerFactory.getLogger(getClass)

  @transient private lazy val mapper: ObjectMapper =
    val m = new ObjectMapper()
    m.registerModule(DefaultScalaModule)
    m

  override def deserialize(message: Array[Byte]): SalesEvent =
    val json = mapper.readTree(message)

    // Handle Debezium CDC envelope: extract the "after" payload for insert/update events
    val payload = if json.has("after") && !json.get("after").isNull then
      val op = if json.has("op") then json.get("op").asText("c") else "c"
      if op == "d" then
        // Delete event - use "before" or skip
        logger.debug("Skipping delete CDC event")
        return null
      json.get("after")
    else if json.has("payload") && json.get("payload").has("after") then
      // Nested envelope from some connect configurations
      val innerPayload = json.get("payload")
      val op = if innerPayload.has("op") then innerPayload.get("op").asText("c") else "c"
      if op == "d" then return null
      innerPayload.get("after")
    else
      json

    SalesEvent(
      saleId = getStringField(payload, "saleId", "sale_id", "id"),
      salesmanName = getStringField(payload, "salesmanName", "salesman_name", "salesman"),
      city = getStringField(payload, "city"),
      country = getStringField(payload, "country"),
      amount = getDoubleField(payload, "amount"),
      product = getStringField(payload, "product"),
      eventTime = getTimestampField(payload, "eventTime", "saleDate", "sale_date", "event_time", "created_at"),
      source = getStringField(payload, "source"),
      ingestionTime = System.currentTimeMillis()
    )

  override def isEndOfStream(nextElement: SalesEvent): Boolean = false

  override def getProducedType: TypeInformation[SalesEvent] =
    TypeInformation.of(new TypeHint[SalesEvent] {})

  private def getStringField(json: JsonNode, names: String*): String =
    names.find(n => json.has(n) && !json.get(n).isNull)
      .map(n => json.get(n).asText(""))
      .getOrElse("")

  private def getDoubleField(json: JsonNode, names: String*): Double =
    names.find(n => json.has(n) && !json.get(n).isNull)
      .map { n =>
        val node = json.get(n)
        if node.isTextual then
          try node.asText("0").toDouble
          catch case _: NumberFormatException => 0.0
        else node.asDouble(0.0)
      }
      .getOrElse(0.0)

  private def getTimestampField(json: JsonNode, names: String*): Long =
    names.find(n => json.has(n) && !json.get(n).isNull)
      .map { n =>
        val node = json.get(n)
        if node.isTextual then
          val text = node.asText("0")
          try text.toLong
          catch case _: NumberFormatException =>
            try java.time.Instant.parse(text).toEpochMilli
            catch case _: Exception =>
              try
                val ldt = java.time.LocalDateTime.parse(text, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                ldt.toInstant(java.time.ZoneOffset.UTC).toEpochMilli
              catch case _: Exception =>
                System.currentTimeMillis()
        else if node.isLong || node.isInt then
          val ts = node.asLong(0L)
          // Debezium may send timestamps as microseconds
          if ts > 1_000_000_000_000_000L then ts / 1000  // microseconds to millis
          else if ts > 1_000_000_000_000L then ts         // already millis
          else ts * 1000                                   // seconds to millis
        else node.asLong(System.currentTimeMillis())
      }
      .getOrElse(System.currentTimeMillis())


class SalesEventSerializationSchema extends SerializationSchema[SalesEvent]:
  @transient private lazy val mapper: ObjectMapper =
    val m = new ObjectMapper()
    m.registerModule(DefaultScalaModule)
    m

  override def serialize(element: SalesEvent): Array[Byte] =
    mapper.writeValueAsBytes(element)
