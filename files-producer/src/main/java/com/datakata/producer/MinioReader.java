package com.datakata.producer;

import io.minio.*;
import io.minio.messages.Item;
import io.minio.messages.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

@Component
public class MinioReader {

    private static final Logger log = LoggerFactory.getLogger(MinioReader.class);
    private static final String TAG_STATUS = "pipeline-status";
    private static final String TAG_PROCESSED = "processed";

    @Value("${minio.endpoint}")
    private String endpoint;

    @Value("${minio.access-key}")
    private String accessKey;

    @Value("${minio.secret-key}")
    private String secretKey;

    @Value("${minio.bucket}")
    private String bucket;

    @Value("${minio.prefix}")
    private String prefix;

    private MinioClient client;

    @PostConstruct
    public void init() {
        client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        log.info("MinIO client initialized. endpoint={}, bucket={}, prefix={}", endpoint, bucket, prefix);
    }

    public List<String> listNewFiles() {
        List<String> newFiles = new ArrayList<>();
        try {
            Iterable<Result<Item>> results = client.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucket)
                            .prefix(prefix)
                            .recursive(true)
                            .build());

            for (Result<Item> result : results) {
                Item item = result.get();
                String key = item.objectName();
                if (key.endsWith(".csv") && !isProcessed(key)) {
                    newFiles.add(key);
                }
            }
        } catch (Exception e) {
            log.error("Failed to list MinIO objects", e);
        }
        return newFiles;
    }

    private boolean isProcessed(String objectKey) {
        try {
            Tags tags = client.getObjectTags(
                    GetObjectTagsArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build());
            return TAG_PROCESSED.equals(tags.get().get(TAG_STATUS));
        } catch (Exception e) {
            log.debug("Could not read tags for {}: {}", objectKey, e.getMessage());
            return false;
        }
    }

    public List<Map<String, String>> readCsv(String objectKey) {
        List<Map<String, String>> rows = new ArrayList<>();
        try (InputStream is = client.getObject(
                GetObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectKey)
                        .build());
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {

            String headerLine = reader.readLine();
            if (headerLine == null) return rows;

            String[] headers = headerLine.split(",");

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] values = line.split(",", -1);
                Map<String, String> row = new HashMap<>();
                for (int i = 0; i < headers.length && i < values.length; i++) {
                    row.put(headers[i].trim(), values[i].trim());
                }
                rows.add(row);
            }

            log.info("Read {} rows from {}", rows.size(), objectKey);
        } catch (Exception e) {
            log.error("Failed to read CSV from MinIO: {}", objectKey, e);
        }
        return rows;
    }

    public void markProcessed(String objectKey) {
        try {
            client.setObjectTags(
                    SetObjectTagsArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .tags(Map.of(TAG_STATUS, TAG_PROCESSED))
                            .build());
            log.info("Tagged {} as processed in MinIO", objectKey);
        } catch (Exception e) {
            log.error("Failed to tag {} as processed in MinIO", objectKey, e);
        }
    }
}
