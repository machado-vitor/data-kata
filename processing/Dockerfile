FROM sbtscala/scala-sbt:eclipse-temurin-17.0.15_6_1.12.5_3.8.2 AS builder

WORKDIR /app
COPY build.sbt .
COPY project/ project/
RUN sbt update

COPY src/ src/
RUN sbt assembly

FROM flink:1.20.3-java17

# Remove Flink's bundled Scala 2.12 to avoid conflict with Scala 3 lambdas
RUN rm -f /opt/flink/lib/flink-scala_2.12-*.jar

# Copy the fat JAR to Flink's usrlib directory
COPY --from=builder /app/target/scala-3.8.2/data-kata-processing.jar /opt/flink/usrlib/data-kata-processing.jar

# Enable Flink metrics prometheus reporter (already bundled, just needs plugin dir)
RUN mkdir -p /opt/flink/plugins/metrics-prometheus && \
    find /opt/flink -name "flink-metrics-prometheus*.jar" -exec cp {} /opt/flink/plugins/metrics-prometheus/ \; 2>/dev/null || true
