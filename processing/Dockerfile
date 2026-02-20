FROM sbtscala/scala-sbt:eclipse-temurin-21.0.2_13_1.9.9_3.3.3 AS builder

WORKDIR /app
COPY build.sbt .
COPY project/ project/
RUN sbt update

COPY src/ src/
RUN sbt assembly

FROM flink:1.19.1-scala_2.12-java21

# Copy the fat JAR to Flink's usrlib directory
COPY --from=builder /app/target/scala-3.3.3/data-kata-processing.jar /opt/flink/usrlib/data-kata-processing.jar

# Copy Flink metrics prometheus reporter
RUN mkdir -p /opt/flink/plugins/metrics-prometheus && \
    cp /opt/flink/opt/flink-metrics-prometheus-*.jar /opt/flink/plugins/metrics-prometheus/
