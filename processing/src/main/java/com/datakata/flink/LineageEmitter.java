package com.datakata.flink;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LineageEmitter {

    private static final Logger logger = LoggerFactory.getLogger(LineageEmitter.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final String PRODUCER = "https://github.com/datakata/flink";
    private static final String SCHEMA_FACET_URL = "https://openlineage.io/spec/facets/1-1-1/SchemaDatasetFacet.json#/$defs/SchemaDatasetFacet";
    private static final String DATASOURCE_FACET_URL = "https://openlineage.io/spec/facets/1-0-1/DatasourceDatasetFacet.json#/$defs/DatasourceDatasetFacet";
    private static final String RUN_EVENT_SCHEMA = "https://openlineage.io/spec/2-0-2/OpenLineage.json#/$defs/RunEvent";
    private static final String JOB_NAMESPACE = "data-kata";

    public record Field(String name, String fieldType) {}
    public record Dataset(String namespace, String name, List<Field> fields) {}

    private static final Map<String, String> DATASOURCES = Map.of(
        "kafka", "kafka://kafka:9092",
        "clickhouse", "clickhouse://clickhouse:8123/datakata"
    );

    private LineageEmitter() {}

    public static void emit(String marquezUrl, String jobName, List<Dataset> inputs, List<Dataset> outputs) {
        var runId = UUID.randomUUID().toString();
        emitEvent(marquezUrl, jobName, "START", inputs, outputs, runId);
        emitEvent(marquezUrl, jobName, "COMPLETE", inputs, outputs, runId);
    }

    private static ArrayNode buildFieldsArray(List<Field> fields) {
        var arr = mapper.createArrayNode();
        for (var f : fields) {
            var node = mapper.createObjectNode();
            node.put("name", f.name());
            node.put("type", f.fieldType());
            arr.add(node);
        }
        return arr;
    }

    private static ObjectNode buildDatasetNode(Dataset ds) {
        var node = mapper.createObjectNode();
        node.put("namespace", ds.namespace());
        node.put("name", ds.name());

        var facets = mapper.createObjectNode();

        var schemaFacet = mapper.createObjectNode();
        schemaFacet.put("_producer", PRODUCER);
        schemaFacet.put("_schemaURL", SCHEMA_FACET_URL);
        schemaFacet.set("fields", buildFieldsArray(ds.fields()));
        facets.set("schema", schemaFacet);

        var uri = DATASOURCES.get(ds.namespace());
        if (uri != null) {
            var sourceFacet = mapper.createObjectNode();
            sourceFacet.put("_producer", PRODUCER);
            sourceFacet.put("_schemaURL", DATASOURCE_FACET_URL);
            sourceFacet.put("name", ds.namespace());
            sourceFacet.put("uri", uri);
            facets.set("datasource", sourceFacet);
        }

        node.set("facets", facets);
        return node;
    }

    private static ArrayNode buildDatasetsArray(List<Dataset> datasets) {
        var arr = mapper.createArrayNode();
        for (var ds : datasets) {
            arr.add(buildDatasetNode(ds));
        }
        return arr;
    }

    private static void emitEvent(String marquezUrl, String jobName, String eventType,
                                   List<Dataset> inputs, List<Dataset> outputs, String runId) {
        try {
            var event = mapper.createObjectNode();
            event.put("eventType", eventType);
            event.put("eventTime", ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            event.put("producer", PRODUCER);
            event.put("schemaURL", RUN_EVENT_SCHEMA);

            var run = mapper.createObjectNode();
            run.put("runId", runId);
            event.set("run", run);

            var job = mapper.createObjectNode();
            job.put("namespace", JOB_NAMESPACE);
            job.put("name", jobName);
            event.set("job", job);

            event.set("inputs", buildDatasetsArray(inputs));
            event.set("outputs", buildDatasetsArray(outputs));

            post(marquezUrl, mapper.writeValueAsString(event), eventType, jobName);
        } catch (Exception e) {
            logger.warn("Failed to emit OpenLineage {} event for {}: {}", eventType, jobName, e.getMessage());
        }
    }

    private static void post(String url, String body, String eventType, String jobName) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            try (var os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            var responseCode = conn.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                logger.info("Emitted OpenLineage {} event for {} (HTTP {})", eventType, jobName, responseCode);
            } else {
                logger.warn("OpenLineage {} event for {} returned HTTP {}", eventType, jobName, responseCode);
            }
        } catch (Exception e) {
            logger.warn("Failed to post OpenLineage event: {}", e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
