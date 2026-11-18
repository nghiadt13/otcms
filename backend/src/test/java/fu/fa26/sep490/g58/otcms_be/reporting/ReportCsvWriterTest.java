package fu.fa26.sep490.g58.otcms_be.reporting;

import static org.junit.jupiter.api.Assertions.*;

import fu.fa26.sep490.g58.otcms_be.reporting.mapper.ReportCsvWriter;
import fu.fa26.sep490.g58.otcms_be.reporting.model.dto.ReportingDtos.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReportCsvWriterTest {
    private static ReportResponse report(List<ColumnResponse> columns, List<RowResponse> rows, TotalRowResponse total) {
        return new ReportResponse(new ReportQueryResponse("X", "2026-11-01", "2026-11-02", null), true, Instant.EPOCH, List.of("export"),
            List.of(new SectionResponse("t", columns, rows, total)));
    }

    @Test void startsWithUtf8BomAndKeepsVietnameseText() {
        byte[] bytes = ReportCsvWriter.write(report(List.of(new ColumnResponse("date", "Ngày", "date"), new ColumnResponse("count", "Số lượng", "count")),
            List.of(new DailyRowResponse("2026-11-01", 3)), new TotalRowResponse(3, null)));
        assertArrayEquals(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, java.util.Arrays.copyOf(bytes, 3));
        assertEquals("Ngày,Số lượng\r\n2026-11-01,3\r\nTổng,3\r\n", new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8));
    }

    @Test void totalRowCarriesCountAndAmountInTheirOwnColumns() {
        byte[] bytes = ReportCsvWriter.write(report(List.of(new ColumnResponse("method", "Phương thức", "text"),
                new ColumnResponse("count", "Số giao dịch", "count"), new ColumnResponse("amount", "Số tiền (VND)", "money")),
            List.of(new PaymentMethodRowResponse("Tiền mặt", 2, 300000), new PaymentMethodRowResponse("PayOS (trực tuyến)", 1, 0)),
            new TotalRowResponse(3, 300000L)));
        String csv = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        assertTrue(csv.contains("Tiền mặt,2,300000\r\n"));
        assertTrue(csv.contains("PayOS (trực tuyến),1,0\r\n"));
        assertTrue(csv.endsWith("Tổng,3,300000\r\n"));
    }

    @Test void aMissingAverageIsAnEmptyCellAndTheTotalHasNoAmountColumn() {
        byte[] bytes = ReportCsvWriter.write(report(List.of(new ColumnResponse("date", "Ngày", "date"),
                new ColumnResponse("count", "Số đánh giá", "count"), new ColumnResponse("average", "Điểm trung bình", "score")),
            List.of(new ReviewDayRowResponse("2026-11-01", 2, 4.5), new ReviewDayRowResponse("2026-11-02", 0, null)),
            new TotalRowResponse(2, null)));
        assertEquals("Ngày,Số đánh giá,Điểm trung bình\r\n2026-11-01,2,4.5\r\n2026-11-02,0,\r\nTổng,2,\r\n",
            new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8));
    }

    @Test void quotesSpecialCharactersAndNeutralisesFormulas() {
        assertEquals("\"a,b\"", ReportCsvWriter.escape("a,b"));
        assertEquals("\"say \"\"hi\"\"\"", ReportCsvWriter.escape("say \"hi\""));
        assertEquals("'=SUM(A1)", ReportCsvWriter.escape("=SUM(A1)"));
        assertEquals("'@cmd", ReportCsvWriter.escape("@cmd"));
        assertEquals("-5", ReportCsvWriter.escape("-5"));
        assertEquals("Lễ tân Hoa", ReportCsvWriter.escape("Lễ tân Hoa"));
    }
}
