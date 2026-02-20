FROM confluentinc/cp-kafka-connect:7.6.1

# Install Debezium PostgreSQL connector
RUN confluent-hub install --no-prompt debezium/debezium-connector-postgresql:2.5.4

# Install Confluent S3 Source connector (works with MinIO)
RUN confluent-hub install --no-prompt confluentinc/kafka-connect-s3-source:2.6.2

# Install Avro converter
RUN confluent-hub install --no-prompt confluentinc/kafka-connect-avro-converter:7.6.1

# Install SpoolDir connector for CSV files from S3/MinIO
RUN confluent-hub install --no-prompt jcustenborder/kafka-connect-spooldir:2.0.65
