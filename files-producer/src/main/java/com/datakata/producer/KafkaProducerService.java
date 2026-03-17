package com.datakata.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

@Component
public class KafkaProducerService {

    private static final Logger log = LoggerFactory.getLogger(KafkaProducerService.class);
    private static final String TOPIC = "sales.files";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${kafka.bootstrap-servers}")
    private String bootstrapServers;

    private KafkaProducer<String, String> producer;

    @PostConstruct
    public void init() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.LINGER_MS_CONFIG, 10);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        producer = new KafkaProducer<>(props);
        log.info("Kafka producer initialized. bootstrap.servers={}", bootstrapServers);
    }

    public void sendRow(Map<String, String> row) throws ExecutionException, InterruptedException {
        try {
            String json = objectMapper.writeValueAsString(row);
            String key = row.getOrDefault("sale_id", "unknown");

            ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, key, json);

            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    log.error("Failed to send sale {} to Kafka topic {}", key, TOPIC, exception);
                } else {
                    log.debug("Sale {} sent to topic {} partition {} offset {}",
                            key, metadata.topic(), metadata.partition(), metadata.offset());
                }
            }).get();
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize row to JSON", e);
            throw new RuntimeException("Failed to serialize row to JSON", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        if (producer != null) {
            log.info("Flushing and closing Kafka producer...");
            try {
                producer.flush();
                producer.close(java.time.Duration.ofSeconds(10));
                log.info("Kafka producer closed successfully.");
            } catch (Exception e) {
                log.error("Error closing Kafka producer", e);
            }
        }
    }
}
