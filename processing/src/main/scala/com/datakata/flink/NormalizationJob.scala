package com.datakata.flink

import com.datakata.flink.model.SalesEvent
import com.datakata.flink.serde.{SalesEventDeserializationSchema, SalesEventSerializationSchema}
import io.openlineage.client.{OpenLineage, OpenLineageClient, OpenLineageClientUtils}
import io.openlineage.client.transports.HttpConfig
import org.apache.flink.api.common.eventtime.{SerializableTimestampAssigner, WatermarkStrategy}
import org.apache.flink.connector.kafka.sink.{KafkaRecordSerializationSchema, KafkaSink}
import org.apache.flink.connector.kafka.source.KafkaSource
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.slf4j.LoggerFactory

import java.net.URI
import java.time.{Duration, ZonedDateTime}
import java.util.UUID

object NormalizationJob:

  private val logger = LoggerFactory.getLogger(getClass)

  def main(args: Array[String]): Unit =
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.enableCheckpointing(30000L)

    val kafkaBootstrap = sys.env.getOrElse("KAFKA_BOOTSTRAP_SERVERS", "kafka:9092")
    val marquezUrl = sys.env.getOrElse("MARQUEZ_URL", "http://marquez:5000/api/v1/lineage")

    val watermarkStrategy = WatermarkStrategy
      .forBoundedOutOfOrderness[SalesEvent](Duration.ofSeconds(5))
      .withTimestampAssigner(new SerializableTimestampAssigner[SalesEvent]:
        override def extractTimestamp(element: SalesEvent, recordTimestamp: Long): Long =
          element.eventTime
      )

    def kafkaSource(topic: String, groupId: String): KafkaSource[SalesEvent] =
      KafkaSource.builder[SalesEvent]()
        .setBootstrapServers(kafkaBootstrap)
        .setTopics(topic)
        .setGroupId(groupId)
        .setStartingOffsets(OffsetsInitializer.earliest())
        .setValueOnlyDeserializer(new SalesEventDeserializationSchema())
        .build()

    val postgresSource = env
      .fromSource(kafkaSource("sales.postgres", "flink-normalization-pg"), watermarkStrategy, "Postgres CDC Source")

    val filesSource = env
      .fromSource(kafkaSource("sales.files", "flink-normalization-files"), watermarkStrategy, "Files Source")

    val legacySource = env
      .fromSource(kafkaSource("sales.legacy", "flink-normalization-legacy"), watermarkStrategy, "Legacy SOAP Source")

    val unified = postgresSource
      .map(e => e.copy(source = "postgres"))
      .union(
        filesSource.map(e => e.copy(source = "files")),
        legacySource.map(e => e.copy(source = "legacy"))
      )
      .map(e => e.copy(ingestionTime = System.currentTimeMillis()))

    val kafkaSink = KafkaSink.builder[SalesEvent]()
      .setBootstrapServers(kafkaBootstrap)
      .setRecordSerializer(
        KafkaRecordSerializationSchema.builder[SalesEvent]()
          .setTopic("sales.unified")
          .setValueSerializationSchema(new SalesEventSerializationSchema())
          .build()
      )
      .build()

    unified.sinkTo(kafkaSink).name("Kafka Sink: sales.unified")

    emitLineageEvent(marquezUrl, "NormalizationJob",
      List("sales.postgres", "sales.files", "sales.legacy"),
      List("sales.unified"))

    logger.info("Starting NormalizationJob - 3 sources -> sales.unified")
    env.execute("NormalizationJob")

  private def emitLineageEvent(marquezUrl: String, jobName: String, inputs: List[String], outputs: List[String]): Unit =
    try
      val config = new HttpConfig()
      config.setUrl(URI.create(marquezUrl))
      val client = new OpenLineageClient(OpenLineageClientUtils.newTransportFromConfig(config))
      val ol = new OpenLineage(URI.create("https://github.com/datakata/flink"))
      val runId = UUID.randomUUID()

      val inputDatasets = inputs.map { topic =>
        ol.newInputDataset("kafka", topic, null, null)
      }
      val outputDatasets = outputs.map { topic =>
        ol.newOutputDataset("kafka", topic, null, null)
      }

      import scala.jdk.CollectionConverters.*
      val event = ol.newRunEventBuilder()
        .eventType(OpenLineage.RunEvent.EventType.START)
        .eventTime(ZonedDateTime.now())
        .run(ol.newRun(runId, null))
        .job(ol.newJob("data-kata", jobName, null))
        .inputs(inputDatasets.asJava)
        .outputs(outputDatasets.asJava)
        .build()

      client.emit(event)
      logger.info("Emitted OpenLineage START event for {}", jobName)
    catch
      case e: Exception =>
        logger.warn("Failed to emit OpenLineage event for {}: {}", jobName, e.getMessage)
