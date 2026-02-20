plugins {
    java
    id("org.springframework.boot") version "3.2.5"
    id("io.spring.dependency-management") version "1.1.5"
}

group = "com.datakata"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-web")

    // Apache CXF JAX-WS client
    implementation("org.apache.cxf:cxf-spring-boot-starter-jaxws:4.0.5")
    implementation("jakarta.xml.ws:jakarta.xml.ws-api:4.0.2")

    // Kafka client
    implementation("org.apache.kafka:kafka-clients:3.7.0")

    // Jackson for JSON serialization
    implementation("com.fasterxml.jackson.core:jackson-databind")
}
