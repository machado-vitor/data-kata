# Tradeoffs ClickHouse

ClickHouse is a columnar database designed for OLAP workloads.
What that means in practice:
- very fast aggregations (SUM, COUNT, AVG, etc.)
- excellent performance on large datasets (billions of rows)
- efficient columnar compression: less I/O

## High ingestion throughput

ClickHouse can handle:
- massive batch inserts
- high-frequency streaming ingestion

Works very well with:
- Apache Kafka pipelines
- real-time analytics systems

## Cost efficiency

Because of compression and columnar storage:
- less disk usage
- fewer nodes needed compared to row-based systems

## Simple architecture (compared to many OLAP systems)

- no heavy dependency on external compute engines
- fewer moving parts than some data warehouse stacks

## Real-time analytics capability

Unlike traditional warehouses, ClickHouse supports:
- near real-time querying
- fast inserts + fast reads simultaneously

So, ideal for:
- dashboards
- monitoring systems
- event analytics

## Limitations
### Not designed for OLTP

ClickHouse is not a transactional database.
- no strong ACID guarantees like traditional relational DBs
- updates and deletes are expensive (mutation-based)
- no efficient row-level operations

### Data mutability is limited

- updates = rewrite operations
- deletes = background merges

With the impact:
- not suitable for frequently changing data
- better for append-only or immutable datasets

### Joins can be expensive

While ClickHouse supports joins:
- they are not as optimized as in traditional relational databases
- large joins can impact performance significantly

### Operational complexity at scale

At small scale: simple

At large scale: more complex:
- shard/replication management
- tuning merges and partitions
- memory management

Requires understanding internal mechanics.

### Limited ecosystem compared to traditional databases

- fewer ORMs
- fewer out-of-the-box integrations
- some tooling still evolving