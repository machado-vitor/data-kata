# Data Kata

A modern end-to-end data pipeline running entirely on Docker Compose. Ingests sales data from three heterogeneous sources (PostgreSQL, CSV files on MinIO, SOAP WS-* service), processes streams through Apache Flink, stores results in ClickHouse, and serves them via a REST API.

## Architecture

```
 ┌──────────────┐    ┌──────────────┐    ┌──────────────┐
 │  PostgreSQL   │    │    MinIO      │    │ SOAP Service │
 │  (Source DB)  │    │ (S3 Files)   │    │  (WS-* API)  │
 └──────┬───────┘    └──────┬───────┘    └──────┬───────┘
        │ pg-producer        │ files-producer    │ ws-producer
        ▼                    ▼                   ▼
 ┌──────────────┐    ┌──────────────┐    ┌──────────────┐
 │sales.postgres│    │ sales.files  │    │ sales.legacy │
 └──────┬───────┘    └──────┬───────┘    └──────┬───────┘
        │                   │                   │
        └───────────┬───────┴───────────────────┘
                    ▼
          ┌─────────────────┐
          │  Apache Flink   │
          │ NormalizationJob│
          └────────┬────────┘
                   ▼
          ┌─────────────────┐
          │  sales.unified  │  (Kafka topic)
          └───────┬─┬───────┘
                  │ │
       ┌──────────┘ └──────────┐
       ▼                       ▼
┌──────────────┐       ┌───────────────┐
│TopSalesCity  │       │TopSalesmanJob │
│    Job       │       │  Country Job  │
└──────┬───────┘       └───────┬───────┘
       │                       │
       ▼                       ▼
┌──────────────────────────────────────┐
│           ClickHouse                  │
│  top_sales_city │ top_salesman_country│
└─────────────────┬────────────────────┘
                  │
                  ▼
          ┌──────────────┐
          │ Results API  │
          │ (Spring Boot)│
          └──────┬───────┘
                 │
    ┌────────────┼────────────┐
    ▼            ▼            ▼
  /top-by-city  /top-salesman /health

  Observability: Prometheus + Grafana
  Data Lineage:  OpenLineage + Marquez
```

## Technology Stack

| Layer | Technology | Language |
|---|---|---|
| Relational DB Source | PostgreSQL 16 | SQL |
| File System Source | MinIO (S3-compatible) | - |
| WS-* Source | Apache CXF SOAP Service | Java 25 |
| Message Broker | Apache Kafka (KRaft) | - |
| Schema Registry | Confluent Schema Registry | - |
| Ingestion - DB | Custom Spring Boot Producer | Java 25 |
| Ingestion - Files | Custom Spring Boot Producer | Java 25 |
| Ingestion - SOAP | Custom Spring Boot Producer | Java 25 |
| Stream Processing | Apache Flink 1.20 | Scala 3 |
| Data Lineage | OpenLineage + Marquez | - |
| Observability | Prometheus + Grafana | - |
| Results Database | ClickHouse | - |
| Results API | Spring Boot 3 (WebFlux) | Kotlin |

## Prerequisites

- Docker Engine 24+
- Docker Compose v2+
- 16 GB RAM allocated to Docker (recommended)
- `make`, `curl`, `jq` installed on host
- Optional: `psql`, `mc` (MinIO client) for seed scripts

## Quick Start

```bash
# Clone and start everything
git clone <repository-url>
cd data-kata
make up
```

This will:
1. Build all Docker images (producers, SOAP service, Flink jobs, Results API)
2. Start all infrastructure (Kafka, PostgreSQL, MinIO, ClickHouse, etc.)
3. Submit Flink processing jobs
4. Seed additional test data

## Service URLs

| Service | URL | Credentials |
|---|---|---|
| Grafana | http://localhost:3000 | admin / admin |
| Marquez UI | http://localhost:3001 | - |
| Flink UI | http://localhost:8081 | - |
| Results API | http://localhost:8080/api/v1 | - |
| MinIO Console | http://localhost:9001 | minioadmin / minioadmin |
| Schema Registry | http://localhost:8085 | - |
| SOAP WSDL | http://localhost:8090/ws/sales?wsdl | - |
| ClickHouse | http://localhost:8123 | datakata / datakata |
| Prometheus | http://localhost:9090 | - |

## API Endpoints

### Top Sales by City
```bash
curl http://localhost:8080/api/v1/sales/top-by-city?window=latest&limit=10
```

### Top Salesman by Country
```bash
curl http://localhost:8080/api/v1/sales/top-salesman?window=latest&country=BR&limit=10
```

### Health Check
```bash
curl http://localhost:8080/api/v1/health
```

## Make Targets

```
make up                 Start everything
make down               Stop everything
make build              Build all images
make submit-flink-jobs  Submit Flink jobs
make seed-data          Generate and load test data
make status             Show service status and URLs
make logs               Follow all logs
make logs-flink         Follow Flink logs
make logs-producers     Follow producer logs
make clean              Remove everything including images
```

## Data Flow

1. **PostgreSQL**: The `pg-producer` polls the sales table every 10 seconds (incremental by max id) and publishes to `sales.postgres` Kafka topic
2. **MinIO Files**: The `files-producer` lists MinIO bucket every 30 seconds, reads new CSV files, and publishes rows to `sales.files` topic
3. **SOAP Service**: The `ws-producer` polls the SOAP service every 30 seconds and publishes to `sales.legacy` topic
4. **Normalization**: Flink `NormalizationJob` consumes all 3 topics, normalizes schemas, and produces to `sales.unified`
5. **Analytics**: Two Flink jobs consume `sales.unified`:
   - `TopSalesCityJob`: 1-hour tumbling windows, ranks top 10 cities by total sales
   - `TopSalesmanCountryJob`: 1-hour tumbling windows, ranks top 10 salesmen in Brazil
6. **Results**: Aggregated results are written to ClickHouse and served via the REST API

## Observability

- **Grafana Dashboard**: Pre-provisioned "Pipeline Health" dashboard with panels for Kafka consumer lag, Flink throughput, checkpoint duration, ClickHouse insert rate, API latency, and error rates
- **Prometheus Alerts**: Configured for high consumer lag, failing Flink checkpoints, and high API latency
- **Data Lineage**: OpenLineage events emitted by Flink jobs are collected by Marquez, visible in the Marquez UI
