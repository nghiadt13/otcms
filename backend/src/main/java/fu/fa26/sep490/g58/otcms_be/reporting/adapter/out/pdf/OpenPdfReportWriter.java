package fu.fa26.sep490.g58.otcms_be.reporting.adapter.out.pdf;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.awt.Color;
import org.openpdf.text.*;
import org.openpdf.text.pdf.*;
import org.springframework.stereotype.Component;

/** Renders only the server's aggregate projection; never HTML or external resources. */
@Component
public class OpenPdfReportWriter implements fu.fa26.sep490.g58.otcms_be.reporting.application.ReportPdfWriter {
    @SuppressWarnings("unchecked")
    public byte[] write(Map<String, Object> report) {
        try {
            var output = new ByteArrayOutputStream();
            var document = new Document(PageSize.A4, 36, 36, 40, 40);
            var writer = PdfWriter.getInstance(document, output);
            var base = BaseFont.createFont("liberation/LiberationSans-Regular.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            var text = new Font(base, 10);
            var heading = new Font(base, 16, Font.BOLD);
            writer.setPageEvent(new PdfPageEventHelper() {
                @Override public void onEndPage(PdfWriter w, Document d) {
                    ColumnText.showTextAligned(w.getDirectContent(), Element.ALIGN_RIGHT,
                            new Phrase("Trang " + w.getPageNumber(), text), d.right(), 22, 0);
                }
            });
            document.open();
            document.add(new Paragraph("OTCMS — BÁO CÁO PHÒNG KHÁM", heading));
            var query = (Map<String, Object>) report.get("query");
            String subject = switch (query.get("subject").toString()) {
                case "APPOINTMENTS" -> "Lịch hẹn theo ngày";
                case "VISITS" -> "Lượt khám theo ngày";
                default -> "Dịch vụ khám từ lịch hẹn đã hoàn tất";
            };
            document.add(new Paragraph(subject, new Font(base, 12, Font.BOLD)));
            document.add(new Paragraph("Kỳ: " + query.get("from") + " — " + query.get("to") + " (giờ Việt Nam)", text));
            document.add(new Paragraph("Thời điểm tạo: " + report.get("generatedAt"), text));
            if (!Boolean.TRUE.equals(report.get("hasData")))
                document.add(new Paragraph("Không có hoạt động trong kỳ này. Tổng số: 0.", text));
            for (var section : (List<Map<String, Object>>) report.get("sections")) {
                var title = new Paragraph(section.get("title").toString(), new Font(base, 12, Font.BOLD));
                title.setSpacingBefore(16); title.setSpacingAfter(8); document.add(title);
                var columns = (List<Map<String, String>>) section.get("columns");
                var table = new PdfPTable(columns.size()); table.setWidthPercentage(100); table.setHeaderRows(1);
                for (var column : columns) {
                    var cell = new PdfPCell(new Phrase(column.get("label"), new Font(base, 10, Font.BOLD)));
                    cell.setBackgroundColor(new Color(232, 242, 241)); cell.setPadding(7); table.addCell(cell);
                }
                for (var row : (List<Map<String, Object>>) section.get("rows")) addRow(table, columns, row, text, false);
                var total = (Map<String, Object>) section.get("totalRow");
                if (total != null) addRow(table, columns, total, new Font(base, 10, Font.BOLD), true);
                document.add(table);
            }
            document.close(); return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("PDF generation failed", exception);
        }
    }

    private void addRow(PdfPTable table, List<Map<String, String>> columns,
                        Map<String, Object> row, Font font, boolean total) {
        for (int i = 0; i < columns.size(); i++) {
            Object value = row.get(columns.get(i).get("key"));
            var cell = new PdfPCell(new Phrase(value == null ? (total && i == 0 ? "Tổng" : "—") : value.toString(), font));
            cell.setPadding(7); table.addCell(cell);
        }
    }
}
