ThisBuild / version := "0.1.0"
ThisBuild / scalaVersion := "3.8.2"
ThisBuild / organization := "com.datakata"

val flinkVersion = "1.20.3"
val jacksonVersion = "2.21.1"

lazy val root = (project in file("."))
  .settings(
    name := "data-kata-processing",
    libraryDependencies ++= Seq(
      // Flink core
      "org.apache.flink" % "flink-streaming-java" % flinkVersion % "provided",
      "org.apache.flink" % "flink-clients" % flinkVersion % "provided",
      "org.apache.flink" % "flink-runtime-web" % flinkVersion % "provided",

      // Flink Kafka connector
      "org.apache.flink" % "flink-connector-kafka" % "3.4.0-1.20",

      // Flink JDBC connector + ClickHouse JDBC
      "org.apache.flink" % "flink-connector-jdbc" % "3.3.0-1.20",
      "com.clickhouse" % "clickhouse-jdbc" % "0.9.7" classifier "all",

      // JSON parsing
      "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,
      "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonVersion,

      // Flink metrics prometheus
      "org.apache.flink" % "flink-metrics-prometheus" % flinkVersion,

      // Logging
      "org.slf4j" % "slf4j-api" % "2.0.17",
      "ch.qos.logback" % "logback-classic" % "1.5.24"
    ),

    // Assembly settings for fat JAR
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", "services", _*) => MergeStrategy.concat
      case PathList("META-INF", _*) => MergeStrategy.discard
      case "reference.conf" => MergeStrategy.concat
      case _ => MergeStrategy.first
    },
    assembly / assemblyJarName := "data-kata-processing.jar"
  )
