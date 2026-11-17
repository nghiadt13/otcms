package fu.fa26.sep490.g58.otcms_be.clinical.service.resultsheet;

import fu.fa26.sep490.g58.otcms_be.common.pdf.PdfFonts;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.springframework.stereotype.Component;

/** Renders an already assembled snapshot; opening a stored PDF never invokes this renderer. */
@Component
public class ResultSheetPdfRenderer {
    private static final float MARGIN = 48;
    private static final float SIZE = 11;
    private static final float LEADING = 16;
    private static final float WIDTH = PDRectangle.A4.getWidth() - 2 * MARGIN;

    public record Snapshot(String patientName, List<String> lines) { }

    public byte[] render(Snapshot snapshot) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var fonts = PdfFonts.load(document);
            List<String> lines = new ArrayList<>();
            lines.addAll(wrap(fonts.regular(), "Bệnh nhân: " + snapshot.patientName()));
            for (String line : snapshot.lines()) lines.addAll(wrap(fonts.regular(), line));

            int index = 0;
            do {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    float y = PDRectangle.A4.getHeight() - MARGIN;
                    if (index == 0) {
                        text(content, fonts.bold(), 15, y, "PHIẾU KẾT QUẢ KHÁM BỆNH");
                        y -= LEADING * 2;
                    }
                    while (index < lines.size() && y >= MARGIN) {
                        text(content, fonts.regular(), SIZE, y, lines.get(index++));
                        y -= LEADING;
                    }
                }
            } while (index < lines.size());
            document.save(output);
            return output.toByteArray();
        } catch (IOException failure) {
            throw new UncheckedIOException("Unable to render result sheet", failure);
        }
    }

    private static void text(PDPageContentStream content, PDFont font, float size, float y, String value) throws IOException {
        content.beginText();
        content.setFont(font, size);
        content.newLineAtOffset(MARGIN, y);
        content.showText(value);
        content.endText();
    }

    /** Keep line breaks and printable glyphs; unsupported characters cannot be encoded by PDFBox. */
    private static String supportedText(PDFont font, String value) throws IOException {
        StringBuilder result = new StringBuilder();
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (codePoint == '\n') {
                result.append('\n');
            } else if (Character.isISOControl(codePoint)) {
                result.append(' ');
            } else {
                String character = new String(Character.toChars(codePoint));
                try {
                    font.encode(character);
                    result.append(character);
                } catch (IllegalArgumentException unsupported) {
                    result.append('?');
                }
            }
        }
        return result.toString();
    }
    /** Wraps each supplied line, including long words, within the printable page width. */
    private static List<String> wrap(PDFont font, String value) throws IOException {
        List<String> result = new ArrayList<>();
        for (String paragraph : supportedText(font, value).split("\\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.trim().split("\\s+")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (font.getStringWidth(candidate) * SIZE / 1000 <= WIDTH) {
                    line.setLength(0);
                    line.append(candidate);
                    continue;
                }
                if (!line.isEmpty()) {
                    result.add(line.toString());
                    line.setLength(0);
                }
                for (int offset = 0; offset < word.length();) {
                    int codePoint = word.codePointAt(offset);
                    String character = new String(Character.toChars(codePoint));
                    if (!line.isEmpty() && font.getStringWidth(line + character) * SIZE / 1000 > WIDTH) {
                        result.add(line.toString());
                        line.setLength(0);
                    }
                    line.append(character);
                    offset += Character.charCount(codePoint);
                }
            }
            result.add(line.toString());
        }
        return result;
    }
}
