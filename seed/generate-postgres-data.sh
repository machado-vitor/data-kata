#!/usr/bin/env bash
set -euo pipefail

# Generate and insert additional sales data into PostgreSQL
# Uses psql to connect to the running PostgreSQL container

PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5432}"
PGUSER="${PGUSER:-datakata}"
PGPASSWORD="${PGPASSWORD:-datakata}"
PGDATABASE="${PGDATABASE:-datakata}"

export PGPASSWORD

CITIES=("São Paulo" "Rio de Janeiro" "Belo Horizonte" "Curitiba" "Porto Alegre" "Salvador" "Brasília" "Florianópolis" "Recife" "Fortaleza")
PRODUCTS=("Laptop" "Monitor" "Keyboard" "Mouse" "Headset" "Webcam" "SSD" "RAM" "GPU" "Motherboard")
SALESMEN=("Carlos Silva" "Ana Oliveira" "Pedro Santos" "Maria Souza" "João Costa" "Fernanda Lima" "Roberto Almeida" "Juliana Ferreira" "Lucas Rodrigues" "Camila Martins" "Gustavo Pereira" "Beatriz Nascimento" "Rafael Araújo" "Larissa Barbosa")
SALESMAN_IDS=(1 2 3 4 5 6 7 8 9 10 11 12 13 14)

# Price ranges per product
declare -A PRICE_MIN
PRICE_MIN=([Laptop]=1500 [Monitor]=350 [Keyboard]=60 [Mouse]=30 [Headset]=50 [Webcam]=40 [SSD]=150 [RAM]=120 [GPU]=600 [Motherboard]=800)
declare -A PRICE_MAX
PRICE_MAX=([Laptop]=5000 [Monitor]=1200 [Keyboard]=250 [Mouse]=150 [Headset]=300 [Webcam]=120 [SSD]=500 [RAM]=400 [GPU]=2500 [Motherboard]=2000)

echo "Generating 100 additional sales records..."

SQL="BEGIN;\n"

for i in $(seq 1 100); do
    idx=$((RANDOM % 14))
    salesman_id="${SALESMAN_IDS[$idx]}"
    salesman="${SALESMEN[$idx]}"

    city_idx=$((RANDOM % 10))
    city="${CITIES[$city_idx]}"

    prod_idx=$((RANDOM % 10))
    product="${PRODUCTS[$prod_idx]}"

    min_price="${PRICE_MIN[$product]}"
    max_price="${PRICE_MAX[$product]}"
    price_range=$((max_price - min_price))
    amount=$((min_price + RANDOM % (price_range + 1)))
    cents=$((RANDOM % 100))

    hour=$((8 + RANDOM % 12))
    minute=$((RANDOM % 60))
    second=$((RANDOM % 60))
    sale_date="2026-02-19 $(printf '%02d' $hour):$(printf '%02d' $minute):$(printf '%02d' $second)"

    # Escape single quotes in names
    escaped_salesman="${salesman//\'/\'\'}"
    escaped_city="${city//\'/\'\'}"

    SQL+="INSERT INTO sales (salesman_id, salesman, city, country, amount, product, sale_date, created_at) "
    SQL+="VALUES ($salesman_id, '${escaped_salesman}', '${escaped_city}', 'BR', ${amount}.${cents}, '${product}', '${sale_date}', NOW());\n"
done

SQL+="COMMIT;\n"

echo -e "$SQL" | psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -q

echo "Inserted 100 additional sales records into PostgreSQL."
