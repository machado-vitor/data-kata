#!/usr/bin/env bash
set -euo pipefail

# Generate CSV files with sales data and upload to MinIO
# Requires: mc (MinIO client) to be installed in the container or host

MINIO_ALIAS="${MINIO_ALIAS:-local}"
MINIO_ENDPOINT="${MINIO_ENDPOINT:-http://localhost:9000}"
MINIO_USER="${MINIO_USER:-minioadmin}"
MINIO_PASSWORD="${MINIO_PASSWORD:-minioadmin}"
BUCKET="sales-data"

CITIES=("São Paulo" "Rio de Janeiro" "Belo Horizonte" "Curitiba" "Porto Alegre" "Salvador" "Brasília" "Florianópolis" "Recife" "Fortaleza")
PRODUCTS=("Laptop" "Monitor" "Keyboard" "Mouse" "Headset" "Webcam" "SSD" "RAM" "GPU" "Motherboard")
SALESMEN=("Carlos Silva" "Ana Oliveira" "Pedro Santos" "Maria Souza" "João Costa" "Fernanda Lima" "Roberto Almeida" "Juliana Ferreira" "Lucas Rodrigues" "Camila Martins")

# Check if mc is available
if ! command -v mc &> /dev/null; then
    echo "MinIO client (mc) not found. Attempting to use docker exec..."
    USE_DOCKER=true
else
    USE_DOCKER=false
    mc alias set "$MINIO_ALIAS" "$MINIO_ENDPOINT" "$MINIO_USER" "$MINIO_PASSWORD" 2>/dev/null || true
fi

generate_csv() {
    local batch_num=$1
    local num_records=$2
    local tmpfile
    tmpfile=$(mktemp /tmp/sales-batch-XXX.csv)

    echo "sale_id,salesman_name,city,country,amount,product,sale_date" > "$tmpfile"

    for i in $(seq 1 "$num_records"); do
        sale_id="G${batch_num}$(printf '%03d' $i)"
        salesman_idx=$((RANDOM % 10))
        city_idx=$((RANDOM % 10))
        product_idx=$((RANDOM % 10))

        amount=$((100 + RANDOM % 4900))
        cents=$((RANDOM % 100))

        hour=$((8 + RANDOM % 12))
        minute=$((RANDOM % 60))
        second=$((RANDOM % 60))
        sale_date="2026-02-19T$(printf '%02d' $hour):$(printf '%02d' $minute):$(printf '%02d' $second)"

        echo "${sale_id},${SALESMEN[$salesman_idx]},${CITIES[$city_idx]},BR,${amount}.$(printf '%02d' $cents),${PRODUCTS[$product_idx]},${sale_date}" >> "$tmpfile"
    done

    echo "$tmpfile"
}

echo "Generating additional CSV batches..."

for batch in 3 4 5; do
    records=$((20 + RANDOM % 30))
    csv_file=$(generate_csv "$batch" "$records")
    filename="sales-batch-$(printf '%03d' $batch).csv"

    if [ "$USE_DOCKER" = true ]; then
        docker cp "$csv_file" datakata-minio:/tmp/"$filename"
        docker exec datakata-minio sh -c "mc alias set local http://localhost:9000 minioadmin minioadmin && mc cp /tmp/$filename local/$BUCKET/topics/sales.files/$filename"
    else
        mc cp "$csv_file" "${MINIO_ALIAS}/${BUCKET}/topics/sales.files/${filename}"
    fi

    rm -f "$csv_file"
    echo "Uploaded $filename with $records records"
done

echo "MinIO seed data upload complete."
