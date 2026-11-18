package fu.fa26.sep490.g58.otcms_be.reporting.mapper;

import fu.fa26.sep490.g58.otcms_be.reporting.model.dto.ReportingDtos.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * UC-78 step 7: renders the very same {@link ReportResponse} the screen shows as CSV, so the file can never disagree
 * with the screen. UTF-8 with a BOM so Excel reads Vietnamese correctly; CRLF line ends per RFC 4180.
 */
public final class ReportCsvWriter {
    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private ReportCsvWriter() {
    }

    public static byte[] write(ReportResponse report) {
        var text = new StringBuilder();
        for (SectionResponse section : report.sections()) {
            appendLine(text, section.columns().stream().map(ColumnResponse::label).toList());
            for (var row : section.rows()) {
                appendLine(text, cells(row).stream().map(ReportCsvWriter::cell).toList());
            }
            // The total comes from the report itself (the same number the screen shows), never recomputed here.
            var total = new ArrayList<String>();
            for (int index = 0; index < section.columns().size(); index++) {
                String key = section.columns().get(index).key();
                total.add(index == 0 ? "Tổng" : key.equals("count") ? String.valueOf(section.totalRow().count())
                    : key.equals("amount") && section.totalRow().amount() != null ? String.valueOf(section.totalRow().amount()) : "");
            }
            appendLine(text, total);
        }
        byte[] body = text.toString().getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[BOM.length + body.length];
        System.arraycopy(BOM, 0, result, 0, BOM.length);
        System.arraycopy(body, 0, result, BOM.length, body.length);
        return result;
    }

    /** Cell values in the same order as the topic's columns. */
    private static List<Object> cells(RowResponse row) {
        return switch (row) {
            case DailyRowResponse r -> List.of(r.date(), r.count());
            case ServiceRowResponse r -> List.of(r.name(), r.count());
            case PaymentDateRowResponse r -> List.of(r.date(), r.count(), r.amount());
            case PaymentReceptionistRowResponse r -> List.of(r.receptionist(), r.count(), r.amount());
            case PaymentMethodRowResponse r -> List.of(r.method(), r.count(), r.amount());
            case OrderGroupRowResponse r -> List.of(r.group(), r.count(), r.amount());
            case ReviewDayRowResponse r -> java.util.Arrays.<Object>asList(r.date(), r.count(), r.average());
        };
    }

    private static String cell(Object value) {
        return value == null ? "" : value.toString();
    }

    private static void appendLine(StringBuilder out, List<String> cells) {
        out.append(String.join(",", cells.stream().map(ReportCsvWriter::escape).toList())).append("\r\n");
    }

    /** Quotes when needed and neutralises spreadsheet formulas (CSV injection) in user-controlled text. */
    public static String escape(String value) {
        String safe = !value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0 && !value.matches("-?\\d+(\\.\\d+)?")
            ? "'" + value : value;
        return safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")
            ? "\"" + safe.replace("\"", "\"\"") + "\"" : safe;
    }
}
