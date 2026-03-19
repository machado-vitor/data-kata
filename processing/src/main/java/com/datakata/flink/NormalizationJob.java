package com.datakata.flink;

import com.datakata.flink.model.SalesEvent;
import com.datakata.flink.serde.SalesEventDeserializationSchema;
import com.datakata.flink.serde.SalesEventSerializationSchema;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;

public class NormalizationJob {

    private static final Logger logger = LoggerFactory.getLogger(NormalizationJob.class);

    public static void main(String[] args) throws Exception {
        var env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.enableCheckpointing(30000L);

        var kafkaBootstrap = System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "kafka:9092");
        var marquezUrl = System.getenv().getOrDefault("MARQUEZ_URL", "http://marquez:5000/api/v1/lineage");

        var watermarkStrategy = WatermarkStrategy.<SalesEvent>forBoundedOutOfOrderness(Duration.ofSeconds(5))
            .withTimestampAssigner((SerializableTimestampAssigner<SalesEvent>) (element, recordTimestamp) -> element.eventTime());

        var postgresSource = env.fromSource(
            kafkaSource(kafkaBootstrap, "sales.postgres", "flink-normalization-pg"),
            watermarkStrategy, "Postgres CDC Source");

        var filesSource = env.fromSource(
            kafkaSource(kafkaBootstrap, "sales.files", "flink-normalization-files"),
            watermarkStrategy, "Files Source");

        var legacySource = env.fromSource(
            kafkaSource(kafkaBootstrap, "sales.legacy", "flink-normalization-legacy"),
            watermarkStrategy, "Legacy SOAP Source");

        var unified = postgresSource
            .map(e -> e.withSource("postgres")).returns(SalesEvent.class)
            .union(
                filesSource.map(e -> e.withSource("files")).returns(SalesEvent.class),
                legacySource.map(e -> e.withSource("legacy")).returns(SalesEvent.class)
            );

        var kafkaSink = KafkaSink.<SalesEvent>builder()
            .setBootstrapServers(kafkaBootstrap)
            .setRecordSerializer(
                KafkaRecordSerializationSchema.<SalesEvent>builder()
                    .setTopic("sales.unified")
                    .setValueSerializationSchema(new SalesEventSerializationSchema())
                    .build()
            )
            .build();

        unified.sinkTo(kafkaSink).name("Kafka Sink: sales.unified");

        LineageEmitter.emit(marquezUrl, "NormalizationJob",
            List.of(Schemas.KAFKA_POSTGRES, Schemas.KAFKA_FILES, Schemas.KAFKA_LEGACY),
            List.of(Schemas.KAFKA_UNIFIED));

        logger.info("Starting NormalizationJob - 3 sources -> sales.unified");
        env.execute("NormalizationJob");
    }

    private static KafkaSource<SalesEvent> kafkaSource(String bootstrap, String topic, String groupId) {
        return KafkaSource.<SalesEvent>builder()
            .setBootstrapServers(bootstrap)
            .setTopics(topic)
            .setGroupId(groupId)
            .setStartingOffsets(OffsetsInitializer.committedOffsets(OffsetResetStrategy.EARLIEST))
            .setValueOnlyDeserializer(new SalesEventDeserializationSchema())
            .build();
    }
}
