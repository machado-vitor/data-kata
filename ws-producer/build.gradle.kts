plugins {
    java
    id("org.springframework.boot") version "4.1.0-M2"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.datakata"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

repositories {
    maven { url = uri("https://repo.spring.io/milestone") }
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-web")

    // Apache CXF JAX-WS client
    implementation("org.apache.cxf:cxf-spring-boot-starter-jaxws:4.1.4")
    implementation("jakarta.xml.ws:jakarta.xml.ws-api:4.0.3")

    // Kafka client
    implementation("org.apache.kafka:kafka-clients:4.2.0")

    // Jackson 3 for JSON serialization (managed by Spring Boot)
    implementation("tools.jackson.core:jackson-databind")
}
