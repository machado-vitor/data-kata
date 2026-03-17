package com.datakata.producer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class PollingScheduler {

    private static final Logger log = LoggerFactory.getLogger(PollingScheduler.class);

    private final MinioReader minioReader;
    private final KafkaProducerService kafkaProducerService;

    public PollingScheduler(MinioReader minioReader, KafkaProducerService kafkaProducerService) {
        this.minioReader = minioReader;
        this.kafkaProducerService = kafkaProducerService;
        log.info("PollingScheduler initialized. Polling MinIO for new CSV files.");
    }

    @Scheduled(fixedDelay = 30000)
    public void pollMinio() {
        log.debug("Polling MinIO for new CSV files...");

        try {
            List<String> newFiles = minioReader.listNewFiles();

            if (newFiles.isEmpty()) {
                log.debug("No new CSV files found.");
                return;
            }

            log.info("Found {} new CSV file(s) in MinIO.", newFiles.size());

            for (String fileKey : newFiles) {
                try {
                    List<Map<String, String>> rows = minioReader.readCsv(fileKey);

                    int successCount = 0;
                    for (Map<String, String> row : rows) {
                        try {
                            kafkaProducerService.sendRow(row);
                            successCount++;
                        } catch (Exception e) {
                            log.error("Failed to send row to Kafka", e);
                        }
                    }

                    if (successCount == rows.size()) {
                        minioReader.markProcessed(fileKey);
                        log.info("File {} processed: {}/{} rows sent to Kafka.", fileKey, successCount, rows.size());
                    } else {
                        log.warn("File {} NOT marked as processed: only {}/{} rows sent successfully.", fileKey, successCount, rows.size());
                    }

                } catch (Exception e) {
                    log.error("Failed to process file {}", fileKey, e);
                }
            }

        } catch (Exception e) {
            log.error("Error during MinIO polling cycle", e);
        }
    }
}
