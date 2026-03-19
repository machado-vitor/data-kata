package com.datakata.flink.serde;

import com.datakata.flink.model.SalesEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.serialization.SerializationSchema;

public class SalesEventSerializationSchema implements SerializationSchema<SalesEvent> {

    private transient ObjectMapper mapper;

    private ObjectMapper mapper() {
        if (mapper == null) mapper = new ObjectMapper();
        return mapper;
    }

    @Override
    public byte[] serialize(SalesEvent element) {
        try {
            return mapper().writeValueAsBytes(element);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize SalesEvent", e);
        }
    }
}
