package com.datakata.flink.serde;

import com.datakata.flink.model.SalesEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class SalesEventDeserializationSchema implements DeserializationSchema<SalesEvent> {

    private static final Logger logger = LoggerFactory.getLogger(SalesEventDeserializationSchema.class);
    private transient ObjectMapper mapper;

    private ObjectMapper mapper() {
        if (mapper == null) mapper = new ObjectMapper();
        return mapper;
    }

    @Override
    public SalesEvent deserialize(byte[] message) throws java.io.IOException {
        var json = mapper().readTree(message);
        var rawJson = new String(message, StandardCharsets.UTF_8);

        var saleId = getStringField(json, "saleId", "sale_id", "id");
        if (saleId.isEmpty()) {
            logger.warn("Missing saleId in record, defaulting to empty string. Raw JSON: {}", rawJson);
        }

        var amount = getDoubleField(json, "amount");
        if (amount == 0.0) {
            logger.warn("Missing or zero amount in record, defaulting to 0.0. Raw JSON: {}", rawJson);
        }

        return new SalesEvent(
            saleId,
            getStringField(json, "salesmanName", "salesman_name", "salesman"),
            getStringField(json, "city"),
            getStringField(json, "country"),
            amount,
            getStringField(json, "product"),
            getTimestampField(json, "eventTime", "saleDate", "sale_date", "event_time", "created_at"),
            getStringField(json, "source"),
            System.currentTimeMillis()
        );
    }

    @Override
    public boolean isEndOfStream(SalesEvent nextElement) {
        return false;
    }

    @Override
    public TypeInformation<SalesEvent> getProducedType() {
        return TypeInformation.of(new TypeHint<SalesEvent>() {});
    }

    private String getStringField(JsonNode json, String... names) {
        for (var name : names) {
            if (json.has(name) && !json.get(name).isNull()) {
                return json.get(name).asText("");
            }
        }
        return "";
    }

    private double getDoubleField(JsonNode json, String... names) {
        for (var name : names) {
            if (json.has(name) && !json.get(name).isNull()) {
                var node = json.get(name);
                if (node.isTextual()) {
                    try {
                        return Double.parseDouble(node.asText("0"));
                    } catch (NumberFormatException e) {
                        return 0.0;
                    }
                }
                return node.asDouble(0.0);
            }
        }
        return 0.0;
    }

    private long getTimestampField(JsonNode json, String... names) {
        for (var name : names) {
            if (json.has(name) && !json.get(name).isNull()) {
                var node = json.get(name);
                if (node.isTextual()) {
                    var text = node.asText("0");
                    try {
                        return Long.parseLong(text);
                    } catch (NumberFormatException e1) {
                        try {
                            return Instant.parse(text).toEpochMilli();
                        } catch (Exception e2) {
                            try {
                                var ldt = LocalDateTime.parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                                return ldt.toInstant(ZoneOffset.UTC).toEpochMilli();
                            } catch (Exception e3) {
                                logger.warn("Failed to parse eventTime from text '{}', falling back to System.currentTimeMillis()", text);
                                return System.currentTimeMillis();
                            }
                        }
                    }
                } else if (node.isLong() || node.isInt()) {
                    var ts = node.asLong(0L);
                    if (ts > 1_000_000_000_000_000L) return ts / 1000;
                    else if (ts > 1_000_000_000_000L) return ts;
                    else return ts * 1000;
                }
                return node.asLong(System.currentTimeMillis());
            }
        }
        logger.warn("No eventTime field found in record, falling back to System.currentTimeMillis()");
        return System.currentTimeMillis();
    }
}
