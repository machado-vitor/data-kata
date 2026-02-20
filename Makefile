.PHONY: up down build deploy-connectors submit-flink-jobs seed-data logs status clean help

FLINK_REST=http://localhost:8081
CONNECT_REST=http://localhost:8083

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}'

up: build ## Start everything
	docker compose up -d
	@echo "Waiting for services to become healthy..."
	@sleep 45
	$(MAKE) deploy-connectors
	@sleep 15
	$(MAKE) submit-flink-jobs
	@sleep 5
	$(MAKE) seed-data
	@echo ""
	@echo "=== Data Kata is running! ==="
	@echo ""
	$(MAKE) status

down: ## Stop everything
	docker compose down -v

build: ## Build all images
	docker compose build

deploy-connectors: ## Deploy Kafka Connect connectors
	@echo "Deploying Debezium PostgreSQL connector..."
	@curl -s -o /dev/null -w "%{http_code}" -X POST $(CONNECT_REST)/connectors \
		-H "Content-Type: application/json" \
		-d @docker/kafka-connect/connectors/debezium-postgres.json || echo " (may already exist)"
	@echo ""
	@echo "Deploying S3/MinIO source connector..."
	@curl -s -o /dev/null -w "%{http_code}" -X POST $(CONNECT_REST)/connectors \
		-H "Content-Type: application/json" \
		-d @docker/kafka-connect/connectors/s3-source-minio.json || echo " (may already exist)"
	@echo ""
	@echo "Connectors deployed. Checking status..."
	@sleep 5
	@curl -s $(CONNECT_REST)/connectors | jq . 2>/dev/null || curl -s $(CONNECT_REST)/connectors

submit-flink-jobs: ## Submit Flink jobs via REST API
	@echo "Submitting NormalizationJob..."
	@curl -s -X POST "$(FLINK_REST)/jars/upload" \
		-F "jarfile=@processing-jar" 2>/dev/null || true
	@echo "Submitting Flink jobs via docker exec..."
	@docker exec datakata-flink-jobmanager flink run -d \
		/opt/flink/usrlib/data-kata-processing.jar \
		--class com.datakata.flink.NormalizationJob 2>/dev/null || \
		echo "NormalizationJob submission (may need manual start via Flink UI)"
	@sleep 5
	@docker exec datakata-flink-jobmanager flink run -d \
		/opt/flink/usrlib/data-kata-processing.jar \
		--class com.datakata.flink.TopSalesCityJob 2>/dev/null || \
		echo "TopSalesCityJob submission (may need manual start via Flink UI)"
	@docker exec datakata-flink-jobmanager flink run -d \
		/opt/flink/usrlib/data-kata-processing.jar \
		--class com.datakata.flink.TopSalesmanCountryJob 2>/dev/null || \
		echo "TopSalesmanCountryJob submission (may need manual start via Flink UI)"
	@echo "Flink jobs submitted."

seed-data: ## Generate and load test data
	@echo "Seeding PostgreSQL with additional data..."
	@bash seed/generate-postgres-data.sh || echo "PostgreSQL seeding skipped (psql not available on host, data loaded via init.sql)"
	@echo "Seeding MinIO with additional CSV files..."
	@bash seed/generate-minio-files.sh || echo "MinIO seeding skipped (mc not available on host, files loaded via minio-init)"
	@echo "Triggering SOAP service..."
	@bash seed/generate-soap-data.sh || echo "SOAP trigger skipped (service may still be starting)"

status: ## Show service status and URLs
	@echo ""
	@echo "=== Service URLs ==="
	@echo "  Grafana:         http://localhost:3000 (admin/admin)"
	@echo "  Marquez UI:      http://localhost:3001"
	@echo "  Flink UI:        http://localhost:8081"
	@echo "  Results API:     http://localhost:8080/api/v1/sales/top-by-city"
	@echo "  MinIO Console:   http://localhost:9001 (minioadmin/minioadmin)"
	@echo "  Schema Registry: http://localhost:8085"
	@echo "  SOAP WSDL:       http://localhost:8090/ws/sales?wsdl"
	@echo "  ClickHouse:      http://localhost:8123"
	@echo "  Prometheus:      http://localhost:9090"
	@echo ""
	@echo "=== Container Status ==="
	@docker compose ps

logs: ## Follow all logs
	docker compose logs -f

logs-flink: ## Follow Flink logs
	docker compose logs -f flink-jobmanager flink-taskmanager

logs-kafka: ## Follow Kafka + Connect logs
	docker compose logs -f kafka kafka-connect

clean: down ## Remove everything including images
	docker compose down -v --rmi all
	@echo "Cleaned up all containers, volumes, and images."
