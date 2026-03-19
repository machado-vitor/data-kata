package com.datakata.producer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Component
public class PollingScheduler {

    private static final Logger log = LoggerFactory.getLogger(PollingScheduler.class);

    private final SoapClient soapClient;
    private final KafkaProducerService kafkaProducerService;

    @Value("${ws-producer.state-dir:/data}")
    private String stateDir;

    private long lastPollTimestamp;

    public PollingScheduler(SoapClient soapClient, KafkaProducerService kafkaProducerService) {
        this.soapClient = soapClient;
        this.kafkaProducerService = kafkaProducerService;
    }

    @PostConstruct
    public void init() {
        lastPollTimestamp = loadTimestamp();
        log.info("PollingScheduler initialized. First poll will fetch sales since timestamp {}", lastPollTimestamp);
    }

    private Path offsetFile() {
        return Path.of(stateDir, "last-poll-timestamp");
    }

    private long loadTimestamp() {
        try {
            Path path = offsetFile();
            if (Files.exists(path)) {
                long ts = Long.parseLong(Files.readString(path).strip());
                log.info("Loaded persisted timestamp from {}: {}", path, ts);
                return ts;
            }
        } catch (Exception e) {
            log.warn("Failed to load persisted timestamp, falling back to 5 minutes ago", e);
        }
        return System.currentTimeMillis() - (5 * 60 * 1000);
    }

    private void saveTimestamp(long timestamp) {
        try {
            Path path = offsetFile();
            Files.createDirectories(path.getParent());
            Files.writeString(path, Long.toString(timestamp));
        } catch (IOException e) {
            log.error("Failed to persist timestamp to {}", offsetFile(), e);
        }
    }

    @Scheduled(fixedDelay = 30000)
    public void pollSoapService() {
        log.info("Polling SOAP service for sales since timestamp {}", lastPollTimestamp);

        try {
            List<SoapClient.Sale> sales = soapClient.getRecentSales(lastPollTimestamp);

            if (sales.isEmpty()) {
                log.info("No new sales returned from SOAP service.");
                return;
            }

            log.info("Received {} sale(s) from SOAP service. Sending to Kafka...", sales.size());

            int successCount = 0;
            for (SoapClient.Sale sale : sales) {
                try {
                    kafkaProducerService.sendSale(sale);
                    successCount++;
                    log.debug("Queued sale {} for Kafka", sale.getSaleId());
                } catch (Exception e) {
                    log.error("Failed to send sale {} to Kafka", sale.getSaleId(), e);
                }
            }

            if (successCount == sales.size()) {
                lastPollTimestamp = System.currentTimeMillis();
                saveTimestamp(lastPollTimestamp);
                log.info("Poll complete. Sent {}/{} sales to Kafka. Updated lastPollTimestamp to {}",
                        successCount, sales.size(), lastPollTimestamp);
            } else {
                log.warn("Poll complete. Only {}/{} sales sent to Kafka. lastPollTimestamp NOT advanced.",
                        successCount, sales.size());
            }

        } catch (Exception e) {
            log.error("Error during SOAP polling cycle", e);
        }
    }
}
