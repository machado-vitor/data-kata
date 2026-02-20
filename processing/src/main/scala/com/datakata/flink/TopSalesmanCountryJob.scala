package com.datakata.flink

import com.datakata.flink.model.SalesEvent
import com.datakata.flink.serde.SalesEventDeserializationSchema
import com.datakata.flink.sink.ClickHouseSink
import io.openlineage.client.{OpenLineage, OpenLineageClient, OpenLineageClientUtils}
import io.openlineage.client.transports.HttpConfig
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

import java.net.URI
import java.time.{Duration, Instant, ZoneOffset, ZonedDateTime}
import java.time.format.DateTimeFormatter
import java.util.UUID
import scala.jdk.CollectionConverters.*

object TopSalesmanCountryJob:

  private val logger = LoggerFactory.getLogger(getClass)
  private val dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC)

  def main(args: Array[String]): Unit =
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.enableCheckpointing(30000L)

    val kafkaBootstrap = sys.env.getOrElse("KAFKA_BOOTSTRAP_SERVERS", "kafka:9092")
    val marquezUrl = sys.env.getOrElse("MARQUEZ_URL", "http://marquez:5000/api/v1/lineage")

    val source = KafkaSource.builder[SalesEvent]()
      .setBootstrapServers(kafkaBootstrap)
      .setTopics("sales.unified")
      .setGroupId("flink-top-salesman-country")
      .setStartingOffsets(OffsetsInitializer.earliest())
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
      .keyBy((_: (String, String, String, String, Double, Long)) => _._1) // window_start
      .process(new TopNSalesmanProcessor(10))
      .addSink(ClickHouseSink.topSalesmanCountrySink)
      .name("ClickHouse Sink: top_salesman_country")

    emitLineageEvent(marquezUrl)

    logger.info("Starting TopSalesmanCountryJob - 1h tumbling windows, top 10 salesmen, BR only")
    env.execute("TopSalesmanCountryJob")

  private def emitLineageEvent(marquezUrl: String): Unit =
    try
      val config = new HttpConfig()
      config.setUrl(URI.create(marquezUrl))
      val client = new OpenLineageClient(OpenLineageClientUtils.newTransportFromConfig(config))
      val ol = new OpenLineage(URI.create("https://github.com/datakata/flink"))

      val event = ol.newRunEventBuilder()
        .eventType(OpenLineage.RunEvent.EventType.START)
        .eventTime(ZonedDateTime.now())
        .run(ol.newRun(UUID.randomUUID(), null))
        .job(ol.newJob("data-kata", "TopSalesmanCountryJob", null))
        .inputs(java.util.List.of(ol.newInputDataset("kafka", "sales.unified", null, null)))
        .outputs(java.util.List.of(ol.newOutputDataset("clickhouse", "top_salesman_country", null, null)))
        .build()
      client.emit(event)
      logger.info("Emitted OpenLineage START event for TopSalesmanCountryJob")
    catch
      case e: Exception =>
        logger.warn("Failed to emit OpenLineage event: {}", e.getMessage)

  // Accumulator: (totalAmount, count)
  class SalesmanAggregator extends AggregateFunction[SalesEvent, (Double, Long), (Double, Long)]:
    override def createAccumulator(): (Double, Long) = (0.0, 0L)
    override def add(value: SalesEvent, acc: (Double, Long)): (Double, Long) =
      (acc._1 + value.amount, acc._2 + 1)
    override def getResult(acc: (Double, Long)): (Double, Long) = acc
    override def merge(a: (Double, Long), b: (Double, Long)): (Double, Long) =
      (a._1 + b._1, a._2 + b._2)

  // Emits (windowStart, windowEnd, salesmanName, country, totalSales, txCount)
  class SalesmanWindowProcessor extends ProcessWindowFunction[(Double, Long), (String, String, String, String, Double, Long), String, TimeWindow]:
    override def process(
        salesmanName: String,
        context: ProcessWindowFunction[(Double, Long), (String, String, String, String, Double, Long), String, TimeWindow]#Context,
        elements: java.lang.Iterable[(Double, Long)],
        out: Collector[(String, String, String, String, Double, Long)]
    ): Unit =
      val window = context.window()
      val windowStart = dtf.format(Instant.ofEpochMilli(window.getStart))
      val windowEnd = dtf.format(Instant.ofEpochMilli(window.getEnd))
      elements.asScala.foreach { case (total, count) =>
        out.collect((windowStart, windowEnd, salesmanName, "BR", total, count))
      }

  class TopNSalesmanProcessor(n: Int)
      extends org.apache.flink.streaming.api.functions.KeyedProcessFunction[
        String,
        (String, String, String, String, Double, Long),
        (String, String, String, String, Double, Long, Int)
      ]:

    import org.apache.flink.api.common.state.{ValueState, ValueStateDescriptor}

    @transient private lazy val bufferState: ValueState[java.util.ArrayList[String]] =
      getRuntimeContext.getState(
        new ValueStateDescriptor[java.util.ArrayList[String]]("salesman-buffer", classOf[java.util.ArrayList[String]])
      )

    override def processElement(
        value: (String, String, String, String, Double, Long),
        ctx: org.apache.flink.streaming.api.functions.KeyedProcessFunction[
          String, (String, String, String, String, Double, Long), (String, String, String, String, Double, Long, Int)
        ]#Context,
        out: Collector[(String, String, String, String, Double, Long, Int)]
    ): Unit =
      var buf = bufferState.value()
      if buf == null then buf = new java.util.ArrayList[String]()
      buf.add(s"${value._3}|${value._4}|${value._5}|${value._6}")
      bufferState.update(buf)
      ctx.timerService().registerProcessingTimeTimer(ctx.timerService().currentProcessingTime() + 2000)

    override def onTimer(
        timestamp: Long,
        ctx: org.apache.flink.streaming.api.functions.KeyedProcessFunction[
          String, (String, String, String, String, Double, Long), (String, String, String, String, Double, Long, Int)
        ]#OnTimerContext,
        out: Collector[(String, String, String, String, Double, Long, Int)]
    ): Unit =
      val buf = bufferState.value()
      if buf != null && !buf.isEmpty then
        val salesmanMap = scala.collection.mutable.Map.empty[String, (String, Double, Long)]
        buf.asScala.foreach { entry =>
          val parts = entry.split("\\|")
          val name = parts(0)
          val country = parts(1)
          val amount = parts(2).toDouble
          val count = parts(3).toLong
          val existing = salesmanMap.getOrElse(name, (country, 0.0, 0L))
          salesmanMap(name) = (existing._1, existing._2 + amount, existing._3 + count)
        }

        val windowKey = ctx.getCurrentKey
        val windowStart = windowKey
        val windowEnd = try
          val startInstant = Instant.from(dtf.parse(windowStart))
          dtf.format(startInstant.plusSeconds(3600))
        catch
          case _: Exception => windowStart

        val ranked = salesmanMap.toList.sortBy(-_._2._2).take(n)
        ranked.zipWithIndex.foreach { case ((name, (country, total, count)), idx) =>
          out.collect((windowStart, windowEnd, name, country, total, count, idx + 1))
        }
        bufferState.clear()
