plugins {
    java
    id("org.springframework.boot") version "3.5.11"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.datakata"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-web")

    // MinIO S3 client
    implementation("io.minio:minio:8.5.14")

    // Kafka client
    implementation("org.apache.kafka:kafka-clients:4.2.0")

    // Jackson for JSON serialization
    implementation("com.fasterxml.jackson.core:jackson-databind")
}
