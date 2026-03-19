FROM gradle:9.4.0-jdk25 AS builder

WORKDIR /app
COPY build.gradle.kts settings.gradle.kts ./
RUN gradle dependencies --no-daemon 2>/dev/null || true

COPY src/ src/
RUN gradle shadowJar --no-daemon

FROM flink:2.2.0-java17

# Copy the fat JAR to Flink's usrlib directory
COPY --from=builder /app/build/libs/data-kata-processing.jar /opt/flink/usrlib/data-kata-processing.jar

# Enable Flink metrics prometheus reporter (already bundled, just needs plugin dir)
RUN mkdir -p /opt/flink/plugins/metrics-prometheus && \
    find /opt/flink -name "flink-metrics-prometheus*.jar" -exec cp {} /opt/flink/plugins/metrics-prometheus/ \; 2>/dev/null || true
