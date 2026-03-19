package com.datakata.api.controller;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    private static final Logger logger = LoggerFactory.getLogger(HealthController.class);

    private final JdbcTemplate jdbcTemplate;
    private final String kafkaBootstrap;
    private volatile AdminClient kafkaAdmin;

    public HealthController(
            JdbcTemplate jdbcTemplate,
            @Value("${kafka.bootstrap-servers:localhost:9092}") String kafkaBootstrap) {
        this.jdbcTemplate = jdbcTemplate;
        this.kafkaBootstrap = kafkaBootstrap;
    }

    public record HealthResponse(String status, String clickhouse, String kafka) {}

    private AdminClient getKafkaAdmin() {
        if (kafkaAdmin == null) {
            synchronized (this) {
                if (kafkaAdmin == null) {
                    kafkaAdmin = AdminClient.create(Map.of(
                        AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrap,
                        AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "5000",
                        AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, "5000"
                    ));
                }
            }
        }
        return kafkaAdmin;
    }

    @GetMapping("/health")
    public Mono<HealthResponse> health() {
        return Mono.fromCallable(() -> {
            var chStatus = checkClickHouse();
            var kafkaStatus = checkKafka();
            var overallStatus = "UP".equals(chStatus) && "UP".equals(kafkaStatus) ? "UP" : "DEGRADED";
            return new HealthResponse(overallStatus, chStatus, kafkaStatus);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private String checkClickHouse() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return "UP";
        } catch (Exception e) {
            logger.warn("ClickHouse health check failed: {}", e.getMessage());
            return "DOWN";
        }
    }

    private String checkKafka() {
        try {
            getKafkaAdmin().listTopics().names().get(5, TimeUnit.SECONDS);
            return "UP";
        } catch (Exception e) {
            logger.warn("Kafka health check failed: {}", e.getMessage());
            return "DOWN";
        }
    }
}
