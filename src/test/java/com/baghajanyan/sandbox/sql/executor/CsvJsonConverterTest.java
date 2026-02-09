package com.baghajanyan.sandbox.sql.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CsvJsonConverterTest {

    @Test
    void toJson_singleRow() {
        var csv = "id,label\n1,value 1";

        var json = CsvJsonConverter.toJson(csv);

        assertEquals("[{\"id\":1,\"label\":\"value 1\"}]", json);
    }

    @Test
    void toJson_multipleRows() {
        var csv = "id,label\n1,value 1\n2,value 2";

        var json = CsvJsonConverter.toJson(csv);

        assertEquals("[{\"id\":1,\"label\":\"value 1\"},{\"id\":2,\"label\":\"value 2\"}]",
                json);
    }

    @Test
    void toJson_quotedComma() {
        var csv = "id,label\n1,\"value, 1\"";

        var json = CsvJsonConverter.toJson(csv);

        assertEquals("[{\"id\":1,\"label\":\"value, 1\"}]", json);
    }

    @Test
    void toJson_escapedQuote() {
        var csv = "id,label\n1,\"value \"\"quoted\"\"\"";

        var json = CsvJsonConverter.toJson(csv);

        assertEquals("[{\"id\":1,\"label\":\"value \\\"quoted\\\"\"}]", json);
    }

    @Test
    void toJson_emptyInput() {
        var json = CsvJsonConverter.toJson("");

        assertEquals("[]", json);
    }

    @Test
    void toJson_multilineField() {
        var csv = "id,label\n1,\"line1\nline2\"";

        var json = CsvJsonConverter.toJson(csv);

        assertEquals("[{\"id\":1,\"label\":\"line1\\nline2\"}]", json);
    }

    @Test
    void toJson_ignoresExecutionTimeMarker() {
        var csv = "id,label\n1,value 1\n__EXECUTION_TIME__: 12";

        var json = CsvJsonConverter.toJson(csv);

        assertEquals("[{\"id\":1,\"label\":\"value 1\"}]", json);
    }

    @Test
    void toJson_ignoresTimingLines() {
        var csv = "id,label\n1,value 1\nTime: 0.12 ms";

        var json = CsvJsonConverter.toJson(csv);

        assertEquals("[{\"id\":1,\"label\":\"value 1\"}]", json);
    }

    @Test
    void toJson_emptyField() {
        var csv = "id,label\n1,\n2,value 2";

        var json = CsvJsonConverter.toJson(csv);

        assertEquals("[{\"id\":1,\"label\":\"\"},{\"id\":2,\"label\":\"value 2\"}]", json);
    }
}
