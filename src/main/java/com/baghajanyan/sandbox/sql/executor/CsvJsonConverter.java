package com.baghajanyan.sandbox.sql.executor;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

final class CsvJsonConverter {
    private static final Logger logger = LoggerFactory.getLogger(CsvJsonConverter.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private CsvJsonConverter() {
    }

    static String toJson(String csv) {
        if (csv == null || csv.isEmpty()) {
            return "[]";
        }
        // Strip timing markers injected by the executor before parsing CSV.
        var cleanedCsv = stripExecutionTimeLines(csv);
        var records = parseCsv(cleanedCsv);
        if (records.isEmpty()) {
            return "[]";
        }
        // The first non-empty record is treated as the header row.
        var headerRecord = firstNonEmptyRecord(records);
        if (headerRecord == null) {
            return "[]";
        }
        var headers = recordToList(headerRecord);
        int headerIndex = records.indexOf(headerRecord);
        int headerSize = headers.size();

        ArrayNode arrayNode = objectMapper.createArrayNode();
        for (int i = headerIndex + 1; i < records.size(); i++) {
            var record = records.get(i);
            if (record.size() != headerSize) {
                logger.debug("Skipping CSV record with unexpected column count. Expected {}, got {}: {}",
                        headerSize, record.size(), record);
                continue;
            }
            var rowNode = objectMapper.createObjectNode();
            for (int c = 0; c < headerSize; c++) {
                putTypedValue(rowNode, headers.get(c), record.get(c));
            }
            arrayNode.add(rowNode);
        }
        try {
            return objectMapper.writeValueAsString(arrayNode);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private static String stripExecutionTimeLines(String csv) {
        return csv.replaceAll("(?m)^\\s*__EXECUTION_TIME__.*$", "")
                .replaceAll("(?m)^Time:.*$", "")
                .trim();
    }

    private static List<CSVRecord> parseCsv(String csv) {
        try (CSVParser parser = CSVFormat.DEFAULT.parse(new StringReader(csv))) {
            return parser.getRecords();
        } catch (IOException e) {
            return List.of();
        }
    }

    private static CSVRecord firstNonEmptyRecord(List<CSVRecord> records) {
        for (var record : records) {
            if (record.size() == 0) {
                continue;
            }
            boolean hasValue = false;
            for (int c = 0; c < record.size(); c++) {
                if (!record.get(c).isBlank()) {
                    hasValue = true;
                    break;
                }
            }
            if (hasValue) {
                return record;
            }
        }
        return null;
    }

    private static List<String> recordToList(CSVRecord record) {
        var values = new ArrayList<String>();
        for (int c = 0; c < record.size(); c++) {
            values.add(record.get(c));
        }
        return values;
    }

    private static void putTypedValue(ObjectNode node, String key, String value) {
        if (value == null) {
            node.putNull(key);
            return;
        }
        var trimmed = value.trim();
        if (trimmed.isEmpty()) {
            node.put(key, "");
            return;
        }
        if ("true".equalsIgnoreCase(trimmed) || "false".equalsIgnoreCase(trimmed)) {
            node.put(key, Boolean.parseBoolean(trimmed));
            return;
        }
        if (trimmed.matches("-?\\d+")) {
            try {
                node.put(key, Long.parseLong(trimmed));
                return;
            } catch (NumberFormatException ignored) {
            }
        }
        if (trimmed.matches("-?\\d*\\.\\d+")) {
            try {
                node.put(key, new BigDecimal(trimmed));
                return;
            } catch (NumberFormatException ignored) {
            }
        }
        node.put(key, value);
    }

}
