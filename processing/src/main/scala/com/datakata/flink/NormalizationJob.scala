package com.datakata.flink

import com.datakata.flink.model.SalesEvent
import com.datakata.flink.serde.{SalesEventDeserializationSchema, SalesEventSerializationSchema}
import org.apache.flink.api.common.eventtime.{SerializableTimestampAssigner, WatermarkStrategy}
import org.apache.flink.connector.kafka.sink.{KafkaRecordSerializationSchema, KafkaSink}
import org.apache.flink.connector.kafka.source.KafkaSource
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.slf4j.LoggerFactory

import java.time.Duration

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
      .map((e: SalesEvent) => e.copy(source = "postgres"))
      .union(
        filesSource.map((e: SalesEvent) => e.copy(source = "files")),
        legacySource.map((e: SalesEvent) => e.copy(source = "legacy"))
      )
      .map((e: SalesEvent) => e.copy(ingestionTime = System.currentTimeMillis()))

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

    LineageEmitter.emit(marquezUrl, "NormalizationJob",
      List("sales.postgres", "sales.files", "sales.legacy"),
      List("sales.unified"))

    logger.info("Starting NormalizationJob - 3 sources -> sales.unified")
    env.execute("NormalizationJob")
