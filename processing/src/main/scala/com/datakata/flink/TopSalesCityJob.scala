package com.datakata.flink

import com.datakata.flink.model.SalesEvent
import com.datakata.flink.serde.SalesEventDeserializationSchema
import com.datakata.flink.sink.ClickHouseSink
import org.apache.flink.api.common.eventtime.{SerializableTimestampAssigner, WatermarkStrategy}
import org.apache.flink.api.common.functions.AggregateFunction
import org.apache.flink.connector.kafka.source.KafkaSource
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.util.Collector
import org.slf4j.LoggerFactory

import java.time.{Duration, Instant, ZoneOffset}
import java.time.format.DateTimeFormatter
import scala.jdk.CollectionConverters.*

object TopSalesCityJob:

  private val logger = LoggerFactory.getLogger(getClass)
  private val dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC)

  // Intermediate type aliases for clarity
  type CityAgg = (String, String, String, Double, Long) // (windowStart, windowEnd, city, totalSales, txCount)
  type CityResult = (String, String, String, Double, Long, Int) // adds rank

  def main(args: Array[String]): Unit =
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.enableCheckpointing(30000L)

    val kafkaBootstrap = sys.env.getOrElse("KAFKA_BOOTSTRAP_SERVERS", "kafka:9092")
    val marquezUrl = sys.env.getOrElse("MARQUEZ_URL", "http://marquez:5000/api/v1/lineage")

    val source = KafkaSource.builder[SalesEvent]()
      .setBootstrapServers(kafkaBootstrap)
      .setTopics("sales.unified")
      .setGroupId("flink-top-sales-city")
      .setStartingOffsets(OffsetsInitializer.committedOffsets(org.apache.kafka.clients.consumer.OffsetResetStrategy.EARLIEST))
      .setValueOnlyDeserializer(new SalesEventDeserializationSchema())
      .build()

    val watermarkStrategy = WatermarkStrategy
      .forBoundedOutOfOrderness[SalesEvent](Duration.ofSeconds(5))
      .withTimestampAssigner(new SerializableTimestampAssigner[SalesEvent]:
        override def extractTimestamp(element: SalesEvent, recordTimestamp: Long): Long =
          element.eventTime
      )

    val stream = env.fromSource(source, watermarkStrategy, "Unified Sales Source")

    stream
      .keyBy((e: SalesEvent) => e.city)
      .window(TumblingEventTimeWindows.of(Time.hours(1)))
      .aggregate(new CityAggregator(), new CityWindowProcessor())
      .keyBy((t: CityAgg) => t._1)
      .process(new TopNCityProcessor(10))
      .addSink(ClickHouseSink.topSalesCitySink)
      .name("ClickHouse Sink: top_sales_city")

    LineageEmitter.emit(marquezUrl, "TopSalesCityJob",
      List(Schemas.kafkaUnified),
      List(Schemas.clickhouseTopCity))

    logger.info("Starting TopSalesCityJob - 1h tumbling windows, top 10 cities")
    env.execute("TopSalesCityJob")

  // Accumulator: (totalAmount, count)
  class CityAggregator extends AggregateFunction[SalesEvent, (Double, Long), (Double, Long)]:
    override def createAccumulator(): (Double, Long) = (0.0, 0L)
    override def add(value: SalesEvent, acc: (Double, Long)): (Double, Long) =
      (acc._1 + value.amount, acc._2 + 1)
    override def getResult(acc: (Double, Long)): (Double, Long) = acc
    override def merge(a: (Double, Long), b: (Double, Long)): (Double, Long) =
      (a._1 + b._1, a._2 + b._2)

  // Emits (windowStart, windowEnd, city, totalSales, txCount)
  class CityWindowProcessor extends ProcessWindowFunction[(Double, Long), CityAgg, String, TimeWindow]:
    override def process(
        city: String,
        context: ProcessWindowFunction[(Double, Long), CityAgg, String, TimeWindow]#Context,
        elements: java.lang.Iterable[(Double, Long)],
        out: Collector[CityAgg]
    ): Unit =
      val window = context.window()
      val windowStart = dtf.format(Instant.ofEpochMilli(window.getStart))
      val windowEnd = dtf.format(Instant.ofEpochMilli(window.getEnd))
      elements.asScala.foreach { case (total, count) =>
        out.collect((windowStart, windowEnd, city, total, count))
      }

  class TopNCityProcessor(n: Int)
      extends org.apache.flink.streaming.api.functions.KeyedProcessFunction[String, CityAgg, CityResult]:

    import org.apache.flink.api.common.state.{ValueState, ValueStateDescriptor}

    // Typed buffer: city -> (windowEnd, totalSales, txCount)
    @transient private lazy val bufferState: ValueState[java.util.HashMap[String, (String, Double, Long)]] =
      getRuntimeContext.getState(
        new ValueStateDescriptor[java.util.HashMap[String, (String, Double, Long)]](
          "city-buffer", classOf[java.util.HashMap[String, (String, Double, Long)]])
      )

    @transient private lazy val timerRegistered: ValueState[java.lang.Boolean] =
      getRuntimeContext.getState(
        new ValueStateDescriptor[java.lang.Boolean]("timer-registered", classOf[java.lang.Boolean])
      )

    override def processElement(
        value: CityAgg,
        ctx: org.apache.flink.streaming.api.functions.KeyedProcessFunction[String, CityAgg, CityResult]#Context,
        out: Collector[CityResult]
    ): Unit =
      var buf = bufferState.value()
      if buf == null then buf = new java.util.HashMap[String, (String, Double, Long)]()
      val city = value._3
      val windowEnd = value._2
      val totalSales = value._4
      val txCount = value._5
      val existing = buf.get(city)
      if existing != null then
        buf.put(city, (windowEnd, existing._2 + totalSales, existing._3 + txCount))
      else
        buf.put(city, (windowEnd, totalSales, txCount))
      bufferState.update(buf)

      val registered = timerRegistered.value()
      if registered == null || !registered then
        ctx.timerService().registerProcessingTimeTimer(ctx.timerService().currentProcessingTime() + 2000)
        timerRegistered.update(java.lang.Boolean.TRUE)

    override def onTimer(
        timestamp: Long,
        ctx: org.apache.flink.streaming.api.functions.KeyedProcessFunction[String, CityAgg, CityResult]#OnTimerContext,
        out: Collector[CityResult]
    ): Unit =
      val buf = bufferState.value()
      if buf != null && !buf.isEmpty then
        val windowStart = ctx.getCurrentKey
        // Grab windowEnd from the first entry (all entries share the same window)
        val windowEnd = buf.values().iterator().next()._1

        val ranked = buf.entrySet().asScala.toList
          .sortBy(-_.getValue._2)
          .take(n)
        ranked.zipWithIndex.foreach { case (entry, idx) =>
          val city = entry.getKey
          val (_, total, count) = entry.getValue
          out.collect((windowStart, windowEnd, city, total, count, idx + 1))
        }
      bufferState.clear()
      timerRegistered.clear()
