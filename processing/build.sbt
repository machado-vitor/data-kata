ThisBuild / version := "0.1.0"
ThisBuild / scalaVersion := "3.3.3"
ThisBuild / organization := "com.datakata"

val flinkVersion = "1.19.1"
val jacksonVersion = "2.15.3"

lazy val root = (project in file("."))
  .settings(
    name := "data-kata-processing",
    resolvers ++= Seq(
      "Apache Snapshots" at "https://repository.apache.org/content/repositories/snapshots/"
    ),
    libraryDependencies ++= Seq(
      // Flink core
      "org.apache.flink" % "flink-streaming-java" % flinkVersion % "provided",
      "org.apache.flink" % "flink-clients" % flinkVersion % "provided",
      "org.apache.flink" % "flink-runtime-web" % flinkVersion % "provided",

      // Flink Kafka connector
      "org.apache.flink" % "flink-connector-kafka" % "3.1.0-1.19",

      // Flink JDBC connector + ClickHouse JDBC
      "org.apache.flink" % "flink-connector-jdbc" % "3.1.2-1.18",
      "com.clickhouse" % "clickhouse-jdbc" % "0.6.0" classifier "all",

      // JSON parsing
      "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,
      "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonVersion,

      // Flink metrics prometheus
      "org.apache.flink" % "flink-metrics-prometheus" % flinkVersion,

      // OpenLineage
      "io.openlineage" % "openlineage-java" % "1.9.1",

      // Logging
      "org.slf4j" % "slf4j-api" % "2.0.12",
      "ch.qos.logback" % "logback-classic" % "1.4.14"
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
