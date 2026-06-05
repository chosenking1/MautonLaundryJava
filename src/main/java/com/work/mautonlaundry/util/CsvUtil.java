package com.work.mautonlaundry.util;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.stream.Collectors;

/** Minimal RFC-4180-style CSV builder for synchronous admin exports. */
public final class CsvUtil {

    private CsvUtil() {
    }

    /** Wrap CSV text as a downloadable attachment response. */
    public static ResponseEntity<String> download(String csv, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }

    public static String toCsv(List<String> headers, List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append(joinRow(headers)).append("\r\n");
        for (List<String> row : rows) {
            sb.append(joinRow(row)).append("\r\n");
        }
        return sb.toString();
    }

    private static String joinRow(List<String> cells) {
        return cells.stream().map(CsvUtil::escape).collect(Collectors.joining(","));
    }

    /** Quote a cell when it contains a comma, quote or newline; double internal quotes. */
    public static String escape(String value) {
        if (value == null) return "";
        boolean mustQuote = value.contains(",") || value.contains("\"")
                || value.contains("\n") || value.contains("\r");
        String escaped = value.replace("\"", "\"\"");
        return mustQuote ? "\"" + escaped + "\"" : escaped;
    }

    public static String s(Object o) {
        return o == null ? "" : o.toString();
    }
}
