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
import java.{util => util}
import scala.jdk.CollectionConverters.*

object TopSalesmanCountryJob:

  private val logger = LoggerFactory.getLogger(getClass)
  private val dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC)

  // Type aliases for readability
  type SalesmanAgg = (String, String, String, String, Double, Long) // (windowStart, windowEnd, salesmanName, country, totalSales, txCount)
  type SalesmanResult = (String, String, String, String, Double, Long, Int) // adds rank

  def main(args: Array[String]): Unit =
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.enableCheckpointing(30000L)

    val kafkaBootstrap = sys.env.getOrElse("KAFKA_BOOTSTRAP_SERVERS", "kafka:9092")
    val marquezUrl = sys.env.getOrElse("MARQUEZ_URL", "http://marquez:5000/api/v1/lineage")

    val source = KafkaSource.builder[SalesEvent]()
      .setBootstrapServers(kafkaBootstrap)
      .setTopics("sales.unified")
      .setGroupId("flink-top-salesman-country")
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
      .filter((e: SalesEvent) => e.country.equalsIgnoreCase("BR"))
      .keyBy((e: SalesEvent) => e.salesmanName)
      .window(TumblingEventTimeWindows.of(Time.hours(1)))
      .aggregate(new SalesmanAggregator(), new SalesmanWindowProcessor())
      .keyBy((t: SalesmanAgg) => t._1)
      .process(new TopNSalesmanProcessor(10))
      .addSink(ClickHouseSink.topSalesmanCountrySink)
      .name("ClickHouse Sink: top_salesman_country")

    LineageEmitter.emit(marquezUrl, "TopSalesmanCountryJob",
      List(Schemas.kafkaUnified),
      List(Schemas.clickhouseTopSalesman))

    logger.info("Starting TopSalesmanCountryJob - 1h tumbling windows, top 10 salesmen, BR only")
    env.execute("TopSalesmanCountryJob")

  // Accumulator: (totalAmount, count)
  class SalesmanAggregator extends AggregateFunction[SalesEvent, (Double, Long), (Double, Long)]:
    override def createAccumulator(): (Double, Long) = (0.0, 0L)
    override def add(value: SalesEvent, acc: (Double, Long)): (Double, Long) =
      (acc._1 + value.amount, acc._2 + 1)
    override def getResult(acc: (Double, Long)): (Double, Long) = acc
    override def merge(a: (Double, Long), b: (Double, Long)): (Double, Long) =
      (a._1 + b._1, a._2 + b._2)

  // Emits (windowStart, windowEnd, salesmanName, country, totalSales, txCount)
  class SalesmanWindowProcessor extends ProcessWindowFunction[(Double, Long), SalesmanAgg, String, TimeWindow]:
    override def process(
        salesmanName: String,
        context: ProcessWindowFunction[(Double, Long), SalesmanAgg, String, TimeWindow]#Context,
        elements: java.lang.Iterable[(Double, Long)],
        out: Collector[SalesmanAgg]
    ): Unit =
      val window = context.window()
      val windowStart = dtf.format(Instant.ofEpochMilli(window.getStart))
      val windowEnd = dtf.format(Instant.ofEpochMilli(window.getEnd))
      elements.asScala.foreach { case (total, count) =>
        out.collect((windowStart, windowEnd, salesmanName, "BR", total, count))
      }

  class TopNSalesmanProcessor(n: Int)
      extends org.apache.flink.streaming.api.functions.KeyedProcessFunction[String, SalesmanAgg, SalesmanResult]:

    import org.apache.flink.api.common.state.{ValueState, ValueStateDescriptor}

    // Typed buffer: salesmanName -> (windowEnd, country, totalSales, txCount)
    @transient private lazy val bufferState: ValueState[util.HashMap[String, (String, String, Double, Long)]] =
      getRuntimeContext.getState(
        new ValueStateDescriptor[util.HashMap[String, (String, String, Double, Long)]](
          "salesman-buffer", classOf[util.HashMap[String, (String, String, Double, Long)]])
      )

    @transient private lazy val timerRegistered: ValueState[java.lang.Boolean] =
      getRuntimeContext.getState(
        new ValueStateDescriptor[java.lang.Boolean]("timer-registered", classOf[java.lang.Boolean])
      )

    override def processElement(
        value: SalesmanAgg,
        ctx: org.apache.flink.streaming.api.functions.KeyedProcessFunction[String, SalesmanAgg, SalesmanResult]#Context,
        out: Collector[SalesmanResult]
    ): Unit =
      var buf = bufferState.value()
      if buf == null then buf = new util.HashMap[String, (String, String, Double, Long)]()
      val name = value._3
      val windowEnd = value._2
      val country = value._4
      val totalSales = value._5
      val txCount = value._6
      val existing = buf.get(name)
      if existing != null then
        buf.put(name, (windowEnd, country, existing._3 + totalSales, existing._4 + txCount))
      else
        buf.put(name, (windowEnd, country, totalSales, txCount))
      bufferState.update(buf)

      val registered = timerRegistered.value()
      if registered == null || !registered then
        ctx.timerService().registerProcessingTimeTimer(ctx.timerService().currentProcessingTime() + 2000)
        timerRegistered.update(java.lang.Boolean.TRUE)

    override def onTimer(
        timestamp: Long,
        ctx: org.apache.flink.streaming.api.functions.KeyedProcessFunction[String, SalesmanAgg, SalesmanResult]#OnTimerContext,
        out: Collector[SalesmanResult]
    ): Unit =
      val buf = bufferState.value()
      if buf != null && !buf.isEmpty then
        val windowStart = ctx.getCurrentKey
        // Grab windowEnd from the first entry (all entries share the same window)
        val firstEntry = buf.values().iterator().next()
        val windowEnd = firstEntry._1

        val ranked = buf.entrySet().asScala.toList
          .sortBy(-_.getValue._3)
          .take(n)
        ranked.zipWithIndex.foreach { case (entry, idx) =>
          val name = entry.getKey
          val (_, country, total, count) = entry.getValue
          out.collect((windowStart, windowEnd, name, country, total, count, idx + 1))
        }
      bufferState.clear()
      timerRegistered.clear()
