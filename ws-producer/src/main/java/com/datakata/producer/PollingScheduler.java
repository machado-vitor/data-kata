package com.datakata.producer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PollingScheduler {

    private static final Logger log = LoggerFactory.getLogger(PollingScheduler.class);

    private final SoapClient soapClient;
    private final KafkaProducerService kafkaProducerService;

    private long lastPollTimestamp;

    public PollingScheduler(SoapClient soapClient, KafkaProducerService kafkaProducerService) {
        this.soapClient = soapClient;
        this.kafkaProducerService = kafkaProducerService;
        // Initialize to 5 minutes ago
        this.lastPollTimestamp = System.currentTimeMillis() - (5 * 60 * 1000);
        log.info("PollingScheduler initialized. First poll will fetch sales since timestamp {}", lastPollTimestamp);
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

            // Update the timestamp to now so the next poll picks up newer records
            lastPollTimestamp = System.currentTimeMillis();
            log.info("Poll complete. Sent {}/{} sales to Kafka. Updated lastPollTimestamp to {}",
                    successCount, sales.size(), lastPollTimestamp);

        } catch (Exception e) {
            log.error("Error during SOAP polling cycle", e);
        }
    }
}
