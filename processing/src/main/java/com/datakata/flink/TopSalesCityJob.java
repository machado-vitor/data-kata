package com.datakata.flink;

import com.datakata.flink.model.CityResult;
import com.datakata.flink.model.SalesEvent;
import com.datakata.flink.serde.SalesEventDeserializationSchema;
import com.datakata.flink.sink.ClickHouseSink;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class TopSalesCityJob {

    private static final Logger logger = LoggerFactory.getLogger(TopSalesCityJob.class);
    private static final DateTimeFormatter DTF =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    public record CityAgg(String windowStart, String windowEnd, String city, double totalSales, long txCount) {}

    public static void main(String[] args) throws Exception {
        var env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.enableCheckpointing(30000L);

        var kafkaBootstrap = System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "kafka:9092");
        var marquezUrl = System.getenv().getOrDefault("MARQUEZ_URL", "http://marquez:5000/api/v1/lineage");

        var source = KafkaSource.<SalesEvent>builder()
            .setBootstrapServers(kafkaBootstrap)
            .setTopics("sales.unified")
            .setGroupId("flink-top-sales-city")
            .setStartingOffsets(OffsetsInitializer.committedOffsets(OffsetResetStrategy.EARLIEST))
            .setValueOnlyDeserializer(new SalesEventDeserializationSchema())
            .build();

        var watermarkStrategy = WatermarkStrategy.<SalesEvent>forBoundedOutOfOrderness(Duration.ofSeconds(5))
            .withTimestampAssigner((SerializableTimestampAssigner<SalesEvent>) (element, recordTimestamp) -> element.eventTime());

        var stream = env.fromSource(source, watermarkStrategy, "Unified Sales Source");

        stream
            .keyBy(SalesEvent::city)
            .window(TumblingEventTimeWindows.of(Duration.ofHours(1)))
            .aggregate(new CityAggregator(), new CityWindowProcessor())
            .keyBy(CityAgg::windowStart)
            .process(new TopNCityProcessor(10))
            .sinkTo(ClickHouseSink.topSalesCitySink())
            .name("ClickHouse Sink: top_sales_city");

        LineageEmitter.emit(marquezUrl, "TopSalesCityJob",
            List.of(Schemas.KAFKA_UNIFIED),
            List.of(Schemas.CLICKHOUSE_TOP_CITY));

        logger.info("Starting TopSalesCityJob - 1h tumbling windows, top 10 cities");
        env.execute("TopSalesCityJob");
    }

    public static class CityAggregator implements AggregateFunction<SalesEvent, double[], double[]> {
        @Override public double[] createAccumulator() { return new double[]{0.0, 0.0}; }
        @Override public double[] add(SalesEvent value, double[] acc) { acc[0] += value.amount(); acc[1] += 1; return acc; }
        @Override public double[] getResult(double[] acc) { return acc; }
        @Override public double[] merge(double[] a, double[] b) { return new double[]{a[0] + b[0], a[1] + b[1]}; }
    }

    public static class CityWindowProcessor extends ProcessWindowFunction<double[], CityAgg, String, TimeWindow> {
        @Override
        public void process(String city, Context context, Iterable<double[]> elements, Collector<CityAgg> out) {
            var window = context.window();
            var windowStart = DTF.format(Instant.ofEpochMilli(window.getStart()));
            var windowEnd = DTF.format(Instant.ofEpochMilli(window.getEnd()));
            for (var elem : elements) {
                out.collect(new CityAgg(windowStart, windowEnd, city, elem[0], (long) elem[1]));
            }
        }
    }

    public static class TopNCityProcessor extends KeyedProcessFunction<String, CityAgg, CityResult> {
        private final int n;
        private transient ValueState<HashMap<String, double[]>> bufferState;
        private transient ValueState<String> windowEndState;
        private transient ValueState<Boolean> timerRegistered;

        public TopNCityProcessor(int n) { this.n = n; }

        @Override
        public void open(OpenContext openContext) {
            bufferState = getRuntimeContext().getState(
                new ValueStateDescriptor<>("city-buffer",
                    TypeInformation.of(new TypeHint<HashMap<String, double[]>>() {})));
            windowEndState = getRuntimeContext().getState(
                new ValueStateDescriptor<>("window-end", String.class));
            timerRegistered = getRuntimeContext().getState(
                new ValueStateDescriptor<>("timer-registered", Boolean.class));
        }

        @Override
        public void processElement(CityAgg value, Context ctx, Collector<CityResult> out) throws Exception {
            var buf = bufferState.value();
            if (buf == null) buf = new HashMap<>();

            windowEndState.update(value.windowEnd());

            var existing = buf.get(value.city());
            if (existing != null) {
                existing[0] += value.totalSales();
                existing[1] += value.txCount();
            } else {
                buf.put(value.city(), new double[]{value.totalSales(), value.txCount()});
            }
            bufferState.update(buf);

            var registered = timerRegistered.value();
            if (registered == null || !registered) {
                ctx.timerService().registerProcessingTimeTimer(ctx.timerService().currentProcessingTime() + 2000);
                timerRegistered.update(true);
            }
        }

        @Override
        public void onTimer(long timestamp, OnTimerContext ctx, Collector<CityResult> out) throws Exception {
            var buf = bufferState.value();
            if (buf != null && !buf.isEmpty()) {
                var windowStart = ctx.getCurrentKey();
                var windowEnd = windowEndState.value();

                var entries = new ArrayList<>(buf.entrySet());
                entries.sort((a, b) -> Double.compare(b.getValue()[0], a.getValue()[0]));

                var top = entries.subList(0, Math.min(n, entries.size()));
                for (int i = 0; i < top.size(); i++) {
                    var entry = top.get(i);
                    out.collect(new CityResult(windowStart, windowEnd, entry.getKey(),
                        entry.getValue()[0], (long) entry.getValue()[1], i + 1));
                }
            }
            bufferState.clear();
            windowEndState.clear();
            timerRegistered.clear();
        }
    }
}
