plugins {
    java
    id("com.gradleup.shadow") version "9.4.0"
}

group = "com.datakata"
version = "0.1.0"

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks.withType<JavaCompile> {
    options.release.set(17)
}

repositories {
    mavenCentral()
}

configurations.all {
    resolutionStrategy.capabilitiesResolution.withCapability("org.lz4:lz4-java") {
        selectHighestVersion()
    }
}

val flinkVersion = "2.2.0"
val jacksonVersion = "2.21.1"

dependencies {
    compileOnly("org.apache.flink:flink-streaming-java:$flinkVersion")
    compileOnly("org.apache.flink:flink-clients:$flinkVersion")

    implementation("org.apache.flink:flink-connector-kafka:4.0.1-2.0")
    implementation("org.apache.flink:flink-connector-jdbc-core:4.0.0-2.0")
    implementation("com.clickhouse:clickhouse-jdbc:0.9.7:all")

    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("org.apache.flink:flink-metrics-prometheus:$flinkVersion")

    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("ch.qos.logback:logback-classic:1.5.24")
}

tasks.shadowJar {
    archiveBaseName.set("data-kata-processing")
    archiveClassifier.set("")
    archiveVersion.set("")
    mergeServiceFiles()
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}
