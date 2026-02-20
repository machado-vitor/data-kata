package com.datakata.api.controller

import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.util.*
import java.util.concurrent.TimeUnit

@RestController
@RequestMapping("/api/v1")
class HealthController(
    private val jdbcTemplate: JdbcTemplate,
    @Value("\${kafka.bootstrap-servers:localhost:9092}") private val kafkaBootstrap: String
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    data class HealthResponse(
        val status: String,
        val clickhouse: String,
        val kafka: String
    )

    @GetMapping("/health")
    fun health(): Mono<HealthResponse> {
        return Mono.fromCallable {
            val chStatus = checkClickHouse()
            val kafkaStatus = checkKafka()
            val overallStatus = if (chStatus == "UP" && kafkaStatus == "UP") "UP" else "DEGRADED"
            HealthResponse(status = overallStatus, clickhouse = chStatus, kafka = kafkaStatus)
        }.subscribeOn(Schedulers.boundedElastic())
    }

    private fun checkClickHouse(): String {
        return try {
            jdbcTemplate.queryForObject("SELECT 1", Int::class.java)
            "UP"
        } catch (e: Exception) {
            logger.warn("ClickHouse health check failed: {}", e.message)
            "DOWN"
        }
    }

    private fun checkKafka(): String {
        return try {
            val props = Properties()
            props[AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG] = kafkaBootstrap
            props[AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG] = "5000"
            props[AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG] = "5000"
            AdminClient.create(props).use { client ->
                client.listTopics().names().get(5, TimeUnit.SECONDS)
            }
            "UP"
        } catch (e: Exception) {
            logger.warn("Kafka health check failed: {}", e.message)
            "DOWN"
        }
    }
}
