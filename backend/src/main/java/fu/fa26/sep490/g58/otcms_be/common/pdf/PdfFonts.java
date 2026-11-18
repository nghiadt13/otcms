package fu.fa26.sep490.g58.otcms_be.common.pdf;

import java.io.IOException;
import java.io.InputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.core.io.ClassPathResource;

/**
 * Embeds the bundled Noto Sans (SIL OFL, see fonts/OFL.txt) into a PDF so Vietnamese text renders correctly;
 * the standard PDF fonts cannot show Vietnamese diacritics. Load once per document.
 */
public final class PdfFonts {
    private static final String REGULAR = "fonts/NotoSans-Regular.ttf";
    private static final String BOLD = "fonts/NotoSans-Bold.ttf";

    private PdfFonts() { }

    public record Family(PDType0Font regular, PDType0Font bold) { }

    /** Both faces are subset-embedded into {@code document}. */
    public static Family load(PDDocument document) throws IOException {
        return new Family(font(document, REGULAR), font(document, BOLD));
    }

    private static PDType0Font font(PDDocument document, String path) throws IOException {
        try (InputStream input = new ClassPathResource(path).getInputStream()) {
            return PDType0Font.load(document, input, true);
        }
    }
}
