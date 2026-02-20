FROM sbtscala/scala-sbt:eclipse-temurin-jammy-17.0.10_7_1.9.9_3.3.3 AS builder

WORKDIR /app
COPY build.sbt .
COPY project/ project/
RUN sbt update

COPY src/ src/
RUN sbt assembly

FROM flink:1.19.1-java17

# Copy the fat JAR to Flink's usrlib directory
COPY --from=builder /app/target/scala-3.3.3/data-kata-processing.jar /opt/flink/usrlib/data-kata-processing.jar

# Enable Flink metrics prometheus reporter (already bundled, just needs plugin dir)
RUN mkdir -p /opt/flink/plugins/metrics-prometheus && \
    find /opt/flink -name "flink-metrics-prometheus*.jar" -exec cp {} /opt/flink/plugins/metrics-prometheus/ \; 2>/dev/null || true
