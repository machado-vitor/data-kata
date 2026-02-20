package com.datakata.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

import java.util.Properties;

@Component
public class KafkaProducerService {

    private static final Logger log = LoggerFactory.getLogger(KafkaProducerService.class);

    private static final String TOPIC = "sales.legacy";
    private static final String SOURCE_VALUE = "legacy";

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

        producer = new KafkaProducer<>(props);
        log.info("Kafka producer initialized. bootstrap.servers={}", bootstrapServers);
    }

    public void sendSale(SoapClient.Sale sale) {
        try {
            ObjectNode json = objectMapper.createObjectNode();
            json.put("saleId", sale.getSaleId());
            json.put("salesmanName", sale.getSalesmanName());
            json.put("city", sale.getCity());
            json.put("country", sale.getCountry());
            json.put("amount", sale.getAmount());
            json.put("product", sale.getProduct());
            json.put("saleDate", sale.getSaleDate());
            json.put("source", SOURCE_VALUE);

            String jsonString = objectMapper.writeValueAsString(json);

            ProducerRecord<String, String> record =
                    new ProducerRecord<>(TOPIC, sale.getSaleId(), jsonString);

            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    log.error("Failed to send sale {} to Kafka topic {}", sale.getSaleId(), TOPIC, exception);
                } else {
                    log.debug("Sale {} sent to topic {} partition {} offset {}",
                            sale.getSaleId(), metadata.topic(), metadata.partition(), metadata.offset());
                }
            });
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize sale {} to JSON", sale.getSaleId(), e);
        }
    }

    @PreDestroy
    public void shutdown() {
        if (producer != null) {
            log.info("Flushing and closing Kafka producer...");
            try {
                producer.flush();
                producer.close();
                log.info("Kafka producer closed successfully.");
            } catch (Exception e) {
                log.error("Error closing Kafka producer", e);
            }
        }
    }
}
