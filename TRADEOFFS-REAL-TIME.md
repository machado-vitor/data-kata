# Real-Time with Flink: Global Window + Trigger per Event

Every incoming sale immediately triggers a re-rank and writes to ClickHouse. No waiting for windows to close.

## Why it sounds great

- Sub-second freshness — rankings update on every event
- Simple model — every event updates the running total

## Why it's not that simple

### Write amplification
Every sale = a full top-10 result set written to ClickHouse. Three producers running continuously means a constant stream of small batches hitting the sink. The current JDBC sink batches at 100 rows or 5s — with continuous triggers, it flushes constantly.

### No time boundaries
There's no `window_start` / `window_end` anymore. Rankings become "all-time" instead of "last hour." The current API, schema, and ReplacingMergeTree key all assume time-bounded windows. Losing that means rewriting the storage layer.

### ClickHouse schema breaks
Tables are keyed by `(window_start, rank)`. Without windows, the dedup strategy needs rethinking. Keying by just `(rank)` means losing all historical data — only the latest ranking survives.

### Replay is messy
On Flink restart from checkpoint, events replay in a burst. Every replayed event fires the trigger, producing intermediate (wrong) rankings until the job catches up. With windows, replay just recomputes the full window cleanly.

### TopN re-sorts on every event
Current approach: collect all cities in a window, sort once, rank once. Continuous triggers: re-sort and re-rank on every single sale. Works fine for 10 cities, doesn't scale.

### State grows forever
Global windows never close. Flink state grows unbounded. You'd need to add manual TTL or cleanup — which is just reimplementing what windows already give you for free.

## Bottom line

You end up solving problems that windows already solve: time boundaries, bounded state, clean replay, controlled write volume. If sub-second freshness isn't a hard requirement, shorter tumbling windows (e.g., 5 minutes) give near real-time without any of these costs.

---

# Flink vs Kafka Streams

Flink is a **cluster**. Kafka Streams is a **library**.

Flink needs its own infrastructure (jobmanager, taskmanagers). Kafka Streams runs inside any JVM app — no extra cluster, no extra deployment.

Flink is source-agnostic — reads from Kafka, JDBC, files, anything. Kafka Streams is Kafka-in, Kafka-out only. Writing to a database requires custom code.

Flink has richer windowing, watermarks, and late data handling. Kafka Streams covers the basics (tumbling, sliding, session) but with less flexibility.

Flink gives you a UI, savepoints, and managed checkpointing. Kafka Streams gives you nothing — monitoring is your problem.

Flink scales by configuring parallelism. Kafka Streams scales by running more app instances — Kafka rebalances partitions automatically.

**Pick Flink** when you need complex event processing, multi-source joins, or advanced windowing. **Pick Kafka Streams** when your input and output are both Kafka and you want simpler ops.

---

# Why Not Spark

Spark depends on the Hadoop ecosystem (`hadoop-client` is a transitive dependency of `spark-core`). The project has a hard restriction against Hadoop. Spark is out.
