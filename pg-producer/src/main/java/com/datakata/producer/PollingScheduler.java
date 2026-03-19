package com.datakata.producer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;

@Component
public class PollingScheduler {

    private static final Logger log = LoggerFactory.getLogger(PollingScheduler.class);

    private final PostgresClient postgresClient;
    private final KafkaProducerService kafkaProducerService;

    private long lastMaxId = 0;

    public PollingScheduler(PostgresClient postgresClient, KafkaProducerService kafkaProducerService) {
        this.postgresClient = postgresClient;
        this.kafkaProducerService = kafkaProducerService;
    }

    @PostConstruct
    public void init() {
        lastMaxId = postgresClient.loadOffset();
        log.info("PollingScheduler initialized. Incremental polling by max(id), starting from {}.", lastMaxId);
    }

    @Scheduled(fixedDelay = 10000)
    public void pollPostgres() {
        log.debug("Polling PostgreSQL for sales with id > {}", lastMaxId);

        try {
            List<Map<String, Object>> rows = postgresClient.fetchNewSales(lastMaxId);

            if (rows.isEmpty()) {
                log.debug("No new sales found.");
                return;
            }

            log.info("Fetched {} new sale(s) from PostgreSQL. Sending to Kafka...", rows.size());

            int successCount = 0;
            for (Map<String, Object> row : rows) {
                try {
                    kafkaProducerService.sendRow(row);
                    successCount++;

                    long id = ((Number) row.get("id")).longValue();
                    if (id > lastMaxId) lastMaxId = id;
                } catch (Exception e) {
                    log.error("Failed to send row to Kafka", e);
                }
            }

            postgresClient.saveOffset(lastMaxId);
            log.info("Poll complete. Sent {}/{} sales to Kafka. lastMaxId={}", successCount, rows.size(), lastMaxId);

        } catch (Exception e) {
            log.error("Error during PostgreSQL polling cycle", e);
        }
    }
}
