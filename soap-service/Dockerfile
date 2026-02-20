FROM gradle:8.7-jdk21 AS builder

WORKDIR /app
COPY build.gradle.kts settings.gradle.kts ./
COPY src/ src/

RUN gradle bootJar --no-daemon

FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]
