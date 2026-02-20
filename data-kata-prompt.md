# Data Kata — Implementation Prompt for Claude CLI

You are tasked with implementing a complete **Data Kata** project: a modern end-to-end data pipeline. The entire system must be runnable locally via `docker compose up` with a single `Makefile` orchestrating all setup steps.

---

## HARD CONSTRAINTS (VIOLATIONS = FAILURE)

1. **NO Python** — not a single .py file, not in scripts, not in tooling, not anywhere
2. **NO Redshift** — no AWS managed DW, use open-source alternatives only
3. **NO Hadoop** — no HDFS, no MapReduce, no Hadoop ecosystem components
4. All code must compile and run. Do not leave TODOs or placeholder implementations.

---

## TECHNOLOGY STACK (MANDATORY)

| Layer | Technology | Language |
|---|---|---|
| Relational DB Source | PostgreSQL 16 | SQL |
| File System Source | MinIO (S3-compatible) | — |
| WS-* Source | Apache CXF SOAP Service | Java 21 |
| Message Broker | Apache Kafka (KRaft mode, no Zookeeper) | — |
| Schema Registry | Confluent Schema Registry | — |
| Ingestion - CDC | Debezium PostgreSQL Connector (via Kafka Connect) | — |
| Ingestion - Files | Kafka Connect S3/MinIO Source Connector | — |
| Ingestion - SOAP | Custom Kafka Producer using Apache CXF client | Java 21 |
| Stream Processing | Apache Flink 1.19+ | Scala 3 with sbt |
| Data Lineage | OpenLineage + Marquez | — |
| Observability | Prometheus + Grafana | — |
| Results Database | ClickHouse | — |
| Results API | Spring Boot 3 (WebFlux) | Kotlin |
| Orchestration | Docker Compose | — |
| Build Automation | Makefile | bash |

---

## PROJECT STRUCTURE

```
data-kata/
├── Makefile
├── docker-compose.yml
├── README.md
│
├── docker/
│   ├── postgres/
│   │   └── init.sql                          # DDL + seed data (50+ rows)
│   ├── clickhouse/
│   │   └── init.sql                          # Result tables DDL
│   ├── kafka-connect/
│   │   ├── Dockerfile                        # Base image + connectors installed
│   │   └── connectors/
│   │       ├── debezium-postgres.json         # Debezium CDC connector config
│   │       └── s3-source-minio.json           # S3/MinIO source connector config
│   ├── minio/
│   │   └── seed-data/
│   │       ├── sales-batch-001.csv
│   │       └── sales-batch-002.csv
│   ├── grafana/
│   │   ├── provisioning/
│   │   │   ├── datasources/
│   │   │   │   └── prometheus.yml
│   │   │   └── dashboards/
│   │   │       └── dashboard.yml
│   │   └── dashboards/
│   │       └── pipeline-health.json           # Pre-built Grafana dashboard
│   └── prometheus/
│       └── prometheus.yml
│
├── soap-service/                              # SOAP WS-* server
│   ├── build.gradle.kts                       # Java 21 + CXF + Spring Boot
│   ├── Dockerfile
│   ├── settings.gradle.kts
│   └── src/main/
│       ├── java/com/datakata/soap/
│       │   ├── SoapServiceApplication.java
│       │   ├── model/
│       │   │   └── Sale.java
│       │   ├── service/
│       │   │   ├── SalesService.java          # SEI (interface with @WebService)
│       │   │   └── SalesServiceImpl.java      # Implementation returning mock sales
│       │   └── config/
│       │       └── CxfConfig.java             # CXF endpoint configuration
│       └── resources/
│           └── application.yml
│
├── ws-producer/                               # SOAP client → Kafka producer
│   ├── build.gradle.kts                       # Java 21 + CXF client + Kafka client
│   ├── Dockerfile
│   ├── settings.gradle.kts
│   └── src/main/java/com/datakata/producer/
│       ├── WsProducerApplication.java
│       ├── SoapClient.java                    # CXF-generated client calling SOAP service
│       ├── KafkaProducerService.java          # Publishes sales events to Kafka (Avro)
│       └── PollingScheduler.java              # Scheduled polling every 30s
│
├── processing/                                # Flink jobs (Scala 3 + sbt)
│   ├── build.sbt
│   ├── project/
│   │   ├── build.properties
│   │   └── plugins.sbt
│   ├── Dockerfile
│   └── src/main/scala/com/datakata/flink/
│       ├── model/
│       │   └── SalesEvent.scala               # Case class + Avro serde
│       ├── serde/
│       │   └── SalesEventSchema.scala         # Kafka deserialization schema
│       ├── sink/
│       │   └── ClickHouseSink.scala           # JDBC sink to ClickHouse
│       ├── NormalizationJob.scala             # 3 sources → unified topic
│       ├── TopSalesCityJob.scala              # Pipeline 1: Top sales per city
│       └── TopSalesmanCountryJob.scala        # Pipeline 2: Top salesman in country
│
├── results-api/                               # REST API serving results
│   ├── build.gradle.kts                       # Kotlin + Spring Boot 3 WebFlux
│   ├── Dockerfile
│   ├── settings.gradle.kts
│   └── src/main/kotlin/com/datakata/api/
│       ├── ResultsApiApplication.kt
│       ├── config/
│       │   └── ClickHouseConfig.kt            # R2DBC or JDBC ClickHouse config
│       ├── model/
│       │   ├── TopSalesCity.kt
│       │   └── TopSalesman.kt
│       ├── repository/
│       │   ├── TopSalesCityRepository.kt
│       │   └── TopSalesmanRepository.kt
│       ├── service/
│       │   └── SalesService.kt
│       └── controller/
│           ├── SalesController.kt             # REST endpoints
│           └── HealthController.kt            # Health + metrics
│
├── seed/                                      # Data generation scripts (bash + jq)
│   ├── generate-postgres-data.sh              # INSERT statements via psql
│   ├── generate-minio-files.sh                # CSV generation + mc upload
│   └── generate-soap-data.sh                  # Trigger SOAP service data refresh
│
└── observability/
    └── alerts/
        └── rules.yml                          # Prometheus alerting rules
```

---

## DETAILED IMPLEMENTATION SPECIFICATIONS

### 1. PostgreSQL Source (`docker/postgres/init.sql`)

Create the following schema and seed with at least 50 realistic Brazilian sales records:

```sql
CREATE TABLE salesman (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    city        VARCHAR(100) NOT NULL,
    region      VARCHAR(100),
    active      BOOLEAN DEFAULT TRUE
);

CREATE TABLE sales (
    id          BIGSERIAL PRIMARY KEY,
    salesman_id BIGINT NOT NULL REFERENCES salesman(id),
    salesman    VARCHAR(255) NOT NULL,
    city        VARCHAR(100) NOT NULL,
    country     VARCHAR(3) NOT NULL DEFAULT 'BR',
    amount      DECIMAL(12,2) NOT NULL,
    product     VARCHAR(255) NOT NULL,
    sale_date   TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Enable logical replication for Debezium
ALTER SYSTEM SET wal_level = logical;
```

Seed data must include cities: São Paulo, Rio de Janeiro, Belo Horizonte, Curitiba, Porto Alegre, Salvador, Brasília, Florianópolis, Recife, Fortaleza. Products: Laptop, Monitor, Keyboard, Mouse, Headset, Webcam, SSD, RAM, GPU, Motherboard.

### 2. SOAP WS-* Service (`soap-service/`)

A Spring Boot app exposing a SOAP endpoint via Apache CXF:

- **WSDL operation**: `GetRecentSales(sinceTimestamp: long) -> List<Sale>`
- **Sale model**: saleId, salesmanName, city, country, amount, product, saleDate
- The service should return randomly generated sales data (different each call) simulating a legacy system
- Endpoint at: `http://soap-service:8090/ws/sales?wsdl`

### 3. WS-* Producer (`ws-producer/`)

A Java app that:
- Polls the SOAP service every 30 seconds using a CXF-generated client
- Serializes each Sale to Avro using the Schema Registry
- Produces to Kafka topic `sales.legacy`
- Includes error handling and retry logic

### 4. Kafka Connect Connectors (`docker/kafka-connect/`)

**Debezium PostgreSQL Connector (`debezium-postgres.json`):**
- Captures CDC from `public.sales` table
- Produces to topic `sales.postgres`
- Uses Avro converter with Schema Registry
- `slot.name: debezium_sales`

**S3/MinIO Source Connector (`s3-source-minio.json`):**
- Reads CSV files from MinIO bucket `sales-data`
- Produces to topic `sales.files`
- Uses Avro converter
- Polls every 60 seconds
- Endpoint override for MinIO: `http://minio:9000`

### 5. Flink Stream Processing (`processing/`)

**build.sbt**: Scala 3.3.x, Flink 1.19.x, flink-connector-kafka, flink-avro, flink-connector-jdbc, ClickHouse JDBC driver.

**NormalizationJob.scala:**
- Consumes from 3 Kafka topics: `sales.postgres`, `sales.files`, `sales.legacy`
- Each source has a slightly different schema — normalize to unified `SalesEvent`:
  ```scala
  case class SalesEvent(
    saleId: String,
    salesmanName: String,
    city: String,
    country: String,
    amount: Double,
    product: String,
    eventTime: Long,      // epoch millis
    source: String,       // "postgres" | "files" | "legacy"
    ingestionTime: Long
  )
  ```
- Produces to `sales.unified` topic
- Assigns watermarks based on `eventTime` with 5-second tolerance

**TopSalesCityJob.scala:**
- Consumes from `sales.unified`
- 1-hour tumbling event-time window
- GROUP BY city → SUM(amount), COUNT(*)
- Compute Top 10 cities by total_sales per window
- Sink to ClickHouse table `top_sales_city`
- Emit OpenLineage events

**TopSalesmanCountryJob.scala:**
- Consumes from `sales.unified`
- Filter by country = 'BR'
- 1-hour tumbling event-time window
- GROUP BY salesman_name → SUM(amount), COUNT(*)
- Compute Top 10 salesmen by total_sales per window
- Sink to ClickHouse table `top_salesman_country`
- Emit OpenLineage events

### 6. ClickHouse Results DB (`docker/clickhouse/init.sql`)

```sql
CREATE TABLE IF NOT EXISTS top_sales_city (
    window_start DateTime,
    window_end   DateTime,
    city         String,
    total_sales  Decimal(18, 2),
    transaction_count UInt32,
    rank         UInt8,
    updated_at   DateTime DEFAULT now()
) ENGINE = ReplacingMergeTree(updated_at)
ORDER BY (window_start, rank);

CREATE TABLE IF NOT EXISTS top_salesman_country (
    window_start    DateTime,
    window_end      DateTime,
    salesman_name   String,
    country         String,
    total_sales     Decimal(18, 2),
    transaction_count UInt32,
    rank            UInt8,
    updated_at      DateTime DEFAULT now()
) ENGINE = ReplacingMergeTree(updated_at)
ORDER BY (window_start, rank);
```

### 7. Results API (`results-api/`)

Spring Boot 3 + Kotlin + WebFlux:

**Endpoints:**
```
GET /api/v1/sales/top-by-city?window=latest&limit=10
    → Returns latest window's top cities ranked by total sales

GET /api/v1/sales/top-salesman?window=latest&country=BR&limit=10
    → Returns latest window's top salesmen ranked by total sales

GET /api/v1/health
    → { "status": "UP", "clickhouse": "UP", "kafka": "UP" }

GET /api/v1/metrics
    → Prometheus format metrics (via Micrometer/Actuator)
```

**Response format (top-by-city):**
```json
{
  "window": { "start": "2026-02-19T10:00:00Z", "end": "2026-02-19T11:00:00Z" },
  "rankings": [
    { "rank": 1, "city": "São Paulo", "totalSales": 125000.00, "transactionCount": 342 },
    { "rank": 2, "city": "Rio de Janeiro", "totalSales": 98000.00, "transactionCount": 267 }
  ]
}
```

Use ClickHouse JDBC driver (not R2DBC — ClickHouse R2DBC is immature). Use `spring-boot-starter-jdbc` with `JdbcTemplate`.

### 8. Data Lineage (OpenLineage + Marquez)

- Marquez server + its PostgreSQL backend as Docker services
- Flink jobs configured with OpenLineage transport:
  ```
  openlineage.transport.type=http
  openlineage.transport.url=http://marquez:5000/api/v1/lineage
  ```
- Marquez UI available at `http://localhost:3001`
- The lineage graph should show the full DAG from sources through normalization to result tables

### 9. Observability

**Prometheus (`docker/prometheus/prometheus.yml`):**
- Scrape targets: Flink JobManager metrics, Kafka JMX exporter, ClickHouse metrics, Results API actuator
- Scrape interval: 15s

**Grafana dashboards (`docker/grafana/dashboards/pipeline-health.json`):**
A pre-provisioned dashboard with panels for:
- Kafka consumer lag per topic
- Flink job throughput (records/sec)
- Flink checkpoint duration
- ClickHouse insert rate
- API request latency (p95)
- Error rate across components

**Alerting rules (`observability/alerts/rules.yml`):**
- High consumer lag (>10000 for 5m)
- Flink checkpoint failing (>60s for 3m)
- API latency p95 > 2s for 5m

### 10. Docker Compose (`docker-compose.yml`)

All services with proper health checks, dependencies (`depends_on` with `condition: service_healthy`), networks, and volumes. Services:

```
postgres, minio, soap-service, kafka (KRaft), schema-registry,
kafka-connect, flink-jobmanager, flink-taskmanager (2 replicas),
clickhouse, results-api, marquez, marquez-db, prometheus, grafana,
ws-producer
```

Use KRaft mode for Kafka (no Zookeeper). Use named volumes for data persistence.

### 11. Makefile

```makefile
.PHONY: up down build deploy-connectors submit-flink-jobs seed-data logs status clean

up: build                           ## Start everything
    docker compose up -d
    @echo "Waiting for services..."
    sleep 30
    $(MAKE) deploy-connectors
    sleep 10
    $(MAKE) submit-flink-jobs
    sleep 5
    $(MAKE) seed-data
    @echo "✅ Data Kata is running!"
    $(MAKE) status

down:                               ## Stop everything
    docker compose down -v

build:                              ## Build all images
    docker compose build

deploy-connectors:                  ## Deploy Kafka Connect connectors
    curl -X POST http://localhost:8083/connectors -H "Content-Type: application/json" -d @docker/kafka-connect/connectors/debezium-postgres.json
    curl -X POST http://localhost:8083/connectors -H "Content-Type: application/json" -d @docker/kafka-connect/connectors/s3-source-minio.json

submit-flink-jobs:                  ## Submit Flink jobs via REST API
    # Submit normalization job first, then pipelines
    ...

seed-data:                          ## Generate and load test data
    bash seed/generate-postgres-data.sh
    bash seed/generate-minio-files.sh

status:                             ## Show service status and URLs
    @echo "📊 Grafana:         http://localhost:3000 (admin/admin)"
    @echo "🔗 Marquez UI:      http://localhost:3001"
    @echo "⚡ Flink UI:        http://localhost:8081"
    @echo "🌐 Results API:     http://localhost:8080/api/v1/sales/top-by-city"
    @echo "📦 MinIO Console:   http://localhost:9001 (minioadmin/minioadmin)"
    @echo "📨 Schema Registry: http://localhost:8085"
    @echo "🏛️ SOAP WSDL:       http://localhost:8090/ws/sales?wsdl"

logs:                               ## Follow all logs
    docker compose logs -f

clean: down                         ## Remove everything including images
    docker compose down -v --rmi all
```

### 12. Seed Data Scripts (`seed/`)

Written in **bash** only (no Python!). Use `psql`, `jq`, `curl`, and MinIO's `mc` CLI.

**`generate-postgres-data.sh`**: Generates 100+ INSERT statements with realistic Brazilian sales data and executes via `psql`.

**`generate-minio-files.sh`**: Generates CSV files with sales data and uploads to MinIO using `mc` CLI.

---

## IMPLEMENTATION ORDER

Implement in this exact order, ensuring each step compiles/works before moving on:

1. `docker-compose.yml` with all infrastructure services (Kafka, PostgreSQL, MinIO, ClickHouse, Marquez, Prometheus, Grafana)
2. `docker/postgres/init.sql` and `docker/clickhouse/init.sql`
3. `soap-service/` — full SOAP WS-* service
4. `ws-producer/` — SOAP client to Kafka producer
5. `docker/kafka-connect/` — Dockerfile and connector configs
6. `processing/` — All three Flink jobs (normalization + 2 pipelines)
7. `results-api/` — REST API
8. `seed/` — Data generation scripts
9. `docker/grafana/` and `docker/prometheus/` — Observability configs
10. `Makefile` and `README.md`

---

## QUALITY REQUIREMENTS

- Every file must be complete — no stubs, no TODOs, no "implement here"
- All Docker images must build successfully
- All Gradle/sbt projects must compile
- Proper error handling everywhere (no swallowed exceptions)
- Proper logging (SLF4J)
- Health checks on all Docker services
- README.md with architecture diagram (ASCII), prerequisites, and step-by-step instructions

---

## BEGIN IMPLEMENTATION

Start by creating the project root directory and implementing each component in the order specified above. Create every file with complete, working code. After creating all files, verify the project structure is complete and provide a summary of what was built.
