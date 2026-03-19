.PHONY: up down build submit-flink-jobs logs status clean help

FLINK_REST=http://localhost:8081

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}'

up: build ## Start everything
	docker compose up -d
	@echo "Waiting for services to become healthy..."
	@sleep 45
	$(MAKE) create-topics
	$(MAKE) submit-flink-jobs
	@echo ""
	@echo "=== Data Kata is running! ==="
	@echo ""
	$(MAKE) status

down: ## Stop everything
	docker compose down -v

build: ## Build all images
	docker compose build

create-topics: ## Pre-create Kafka topics required by Flink
	@echo "Creating Kafka topics..."
	@docker exec datakata-kafka kafka-topics --bootstrap-server localhost:9092 \
		--create --topic sales.unified --partitions 1 --replication-factor 1 --if-not-exists 2>/dev/null || true
	@echo "Topics ready."

submit-flink-jobs: ## Submit Flink jobs via REST API
	@echo "Submitting Flink jobs..."
	@docker exec datakata-flink-jobmanager flink run -d \
		-c com.datakata.flink.NormalizationJob \
		/opt/flink/usrlib/data-kata-processing.jar 2>/dev/null || \
		echo "NormalizationJob submission (may need manual start via Flink UI)"
	@sleep 5
	@docker exec datakata-flink-jobmanager flink run -d \
		-c com.datakata.flink.TopSalesCityJob \
		/opt/flink/usrlib/data-kata-processing.jar 2>/dev/null || \
		echo "TopSalesCityJob submission (may need manual start via Flink UI)"
	@docker exec datakata-flink-jobmanager flink run -d \
		-c com.datakata.flink.TopSalesmanCountryJob \
		/opt/flink/usrlib/data-kata-processing.jar 2>/dev/null || \
		echo "TopSalesmanCountryJob submission (may need manual start via Flink UI)"
	@echo "Flink jobs submitted."

status: ## Show service status and URLs
	@echo ""
	@echo "=== Service URLs ==="
	@echo "  Grafana:         http://localhost:3000 (admin/admin)"
	@echo "  Marquez UI:      http://localhost:3001"
	@echo "  Flink UI:        http://localhost:8081"
	@echo "  Results API:     http://localhost:8080/api/v1/sales/top-by-city"
	@echo "  MinIO Console:   http://localhost:9001 (minioadmin/minioadmin)"
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

logs-producers: ## Follow producer logs
	docker compose logs -f pg-producer files-producer ws-producer

clean: down ## Remove everything including images
	docker compose down -v --rmi all
	@echo "Cleaned up all containers, volumes, and images."
