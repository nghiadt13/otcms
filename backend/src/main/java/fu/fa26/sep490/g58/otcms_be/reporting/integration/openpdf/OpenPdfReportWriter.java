package fu.fa26.sep490.g58.otcms_be.reporting.integration.openpdf;

import fu.fa26.sep490.g58.otcms_be.reporting.model.ReportLabels;
import fu.fa26.sep490.g58.otcms_be.reporting.model.ReportResult;
import fu.fa26.sep490.g58.otcms_be.reporting.service.ReportPdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.ColumnText;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfPageEventHelper;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

/** Text-only rendering of validated aggregates; no external resources or report logging. */
@Component
public class OpenPdfReportWriter implements ReportPdfWriter {
    private static final DateTimeFormatter CREATED_AT = DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm:ss")
        .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    @Override
    public byte[] write(ReportResult report) {
        var output = new ByteArrayOutputStream();
        var document = new Document(PageSize.A4, 36, 36, 40, 42);
        try {
            var writer = PdfWriter.getInstance(document, output);
            var base = BaseFont.createFont("liberation/LiberationSans-Regular.ttf",
                BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            var boldBase = BaseFont.createFont("liberation/LiberationSans-Bold.ttf",
                BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            var text = new Font(base, 10);
            var bold = new Font(boldBase, 10);
            writer.setPageEvent(new PdfPageEventHelper() {
                @Override
                public void onEndPage(PdfWriter pageWriter, Document page) {
                    ColumnText.showTextAligned(pageWriter.getDirectContent(), Element.ALIGN_RIGHT,
                        new Phrase("Trang " + pageWriter.getPageNumber(), text), page.right(), 22, 0);
                }
            });
            document.open();
            document.add(new Paragraph("OTCMS — BÁO CÁO PHÒNG KHÁM", new Font(boldBase, 16)));
            var title = new Paragraph(ReportLabels.subject(report.subject()), new Font(boldBase, 12));
            title.setSpacingBefore(10);
            title.setSpacingAfter(8);
            document.add(title);
            document.add(new Paragraph("Kỳ: " + report.period().from() + " — " + report.period().to()
                + " (giờ Việt Nam)", text));
            document.add(new Paragraph("Thời điểm tạo: " + CREATED_AT.format(report.generatedAt())
                + " (giờ Việt Nam)", text));
            if (report.total() == 0) {
                document.add(new Paragraph("Không có hoạt động trong kỳ này. Tổng số: 0.", text));
            }
            boolean services = report.subject().equals("SERVICES");
            var table = new PdfPTable(2);
            table.setWidths(new float[] {3, 2});
            table.setWidthPercentage(100);
            table.setHeaderRows(1);
            table.setSpacingBefore(16);
            addCell(table, services ? ReportLabels.SERVICE : ReportLabels.DATE, bold, true);
            addCell(table, services ? ReportLabels.COMPLETED : ReportLabels.COUNT, bold, true);
            if (services) {
                for (var row : report.services()) {
                    addCell(table, row.name(), text, false);
                    addCell(table, Long.toString(row.value()), text, false);
                }
            } else {
                for (var row : report.daily()) {
                    addCell(table, row.date(), text, false);
                    addCell(table, Long.toString(row.count()), text, false);
                }
            }
            addCell(table, "Tổng", bold, true);
            addCell(table, Long.toString(report.total()), bold, true);
            document.add(table);
            document.close();
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("PDF generation failed", exception);
        } finally {
            if (document.isOpen()) {
                document.close();
            }
        }
    }

    private void addCell(PdfPTable table, String value, Font font, boolean emphasized) {
        var cell = new PdfPCell(new Phrase(value, font));
        cell.setPadding(7);
        cell.setBorderColor(new Color(205, 219, 218));
        if (emphasized) {
            cell.setBackgroundColor(new Color(232, 242, 241));
        }
        table.addCell(cell);
    }
}
