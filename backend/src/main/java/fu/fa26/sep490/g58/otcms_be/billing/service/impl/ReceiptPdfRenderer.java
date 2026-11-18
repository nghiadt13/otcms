package fu.fa26.sep490.g58.otcms_be.billing.service.impl;

import fu.fa26.sep490.g58.otcms_be.billing.exception.BillingException;
import fu.fa26.sep490.g58.otcms_be.billing.service.BillingDetailService.Line;
import fu.fa26.sep490.g58.otcms_be.billing.service.BillingDetailService.PaymentDetail;
import fu.fa26.sep490.g58.otcms_be.common.pdf.PdfFonts;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.springframework.stereotype.Component;

/** Draws the one-page receipt of a recorded payment (UC-58): patient, items, amounts, method, date, receiver or PayOS reference. */
@Component
public class ReceiptPdfRenderer {
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final float MARGIN = 48, LEADING = 16, SIZE = 11;
    private static final float DESCRIPTION_WIDTH = 250, QUANTITY_X = 330, PRICE_X = 380, TOTAL_X = 470;

    public byte[] render(PaymentDetail payment) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var fonts = PdfFonts.load(document);
            // A4 page, taller when many item lines would not fit.
            int lineCount = 12;
            for (Line item : payment.items()) lineCount += wrap(fonts.regular(), item.lineNumber() + ". " + item.description()).size();
            PDPage page = new PDPage(new PDRectangle(PDRectangle.A4.getWidth(),
                    Math.max(PDRectangle.A4.getHeight(), 2 * MARGIN + LEADING * lineCount)));
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                float y = page.getMediaBox().getHeight() - MARGIN;
                y = text(content, fonts.bold(), 15, MARGIN, y, "BIÊN LAI THANH TOÁN") - 6;
                y = line(content, fonts.regular(), y, "Số biên lai: " + payment.receiptNumber());
                y = line(content, fonts.regular(), y, "Hóa đơn: " + payment.invoiceNumber());
                y = line(content, fonts.regular(), y, "Bệnh nhân: " + orDash(payment.patientName()));
                y = line(content, fonts.regular(), y, "Ngày thanh toán: " + DATE_TIME.format(payment.confirmedAt().atZone(CLINIC_ZONE)));
                y = line(content, fonts.regular(), y, "Phương thức: " + ("PAYOS".equals(payment.method()) ? "PayOS" : "Tiền mặt"));
                y = line(content, fonts.regular(), y, payment.providerTransactionReference() != null
                        ? "Mã giao dịch PayOS: " + payment.providerTransactionReference()
                        : "Người thu: " + orDash(payment.receivedByName()));
                y -= LEADING / 2;
                y = text(content, fonts.bold(), SIZE, MARGIN, y, "Khoản");
                text(content, fonts.bold(), SIZE, QUANTITY_X, y + LEADING, "SL");
                text(content, fonts.bold(), SIZE, PRICE_X, y + LEADING, "Đơn giá");
                text(content, fonts.bold(), SIZE, TOTAL_X, y + LEADING, "Thành tiền");
                for (Line item : payment.items()) {
                    List<String> description = wrap(fonts.regular(), item.lineNumber() + ". " + item.description());
                    float top = y;
                    for (String part : description) y = text(content, fonts.regular(), SIZE, MARGIN, y, part);
                    text(content, fonts.regular(), SIZE, QUANTITY_X, top, String.valueOf(item.quantity()));
                    text(content, fonts.regular(), SIZE, PRICE_X, top, money(item.unitPrice()));
                    text(content, fonts.regular(), SIZE, TOTAL_X, top, money(item.lineTotal()));
                }
                y -= LEADING / 2;
                text(content, fonts.bold(), 13, MARGIN, y, "Tổng thu: " + money(payment.amount()) + " " + payment.currency());
            }
            document.save(out);
            return out.toByteArray();
        } catch (IOException | RuntimeException failure) {
            throw new BillingException("MSG-04", failure);
        }
    }

    /** Draws one line and returns the y of the next line. */
    private static float text(PDPageContentStream content, PDFont font, float size, float x, float y, String value) throws IOException {
        content.beginText();
        content.setFont(font, size);
        content.newLineAtOffset(x, y);
        content.showText(value);
        content.endText();
        return y - LEADING;
    }

    private static float line(PDPageContentStream content, PDFont font, float y, String value) throws IOException {
        return text(content, font, SIZE, MARGIN, y, value);
    }

    /** Breaks a description on spaces so it stays inside its column. */
    private static List<String> wrap(PDFont font, String value) throws IOException {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : value.split(" ")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (!current.isEmpty() && font.getStringWidth(candidate) / 1000 * SIZE > DESCRIPTION_WIDTH) {
                lines.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(candidate);
            }
        }
        lines.add(current.toString());
        return lines;
    }

    private static String money(BigDecimal amount) {
        return NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(amount);
    }

    private static String orDash(String value) { return value == null || value.isBlank() ? "-" : value; }
}
