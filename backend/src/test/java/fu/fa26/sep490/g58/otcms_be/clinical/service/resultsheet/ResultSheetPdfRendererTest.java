package fu.fa26.sep490.g58.otcms_be.clinical.service.resultsheet;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class ResultSheetPdfRendererTest {
    @Test
    void handlesControlCharactersAndUnsupportedGlyphsInFreeText() throws Exception {
        byte[] bytes = new ResultSheetPdfRenderer().render(new ResultSheetPdfRenderer.Snapshot(
                "Nguyễn Thị Ánh", List.of("Lời dặn: Nghỉ ngơi" + (char) 1 + " và tái khám \uD83D\uDE00")));
        try (var document = Loader.loadPDF(bytes)) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("Nguyễn Thị Ánh"), text);
            assertTrue(text.contains("Lời dặn: Nghỉ ngơi và tái khám ?"), text);
        }
    }

    @Test
    void rendersVietnameseAndPatientNameAcrossPages() throws Exception {
        List<String> lines = new ArrayList<>();
        lines.add("Lý do khám: Đau khớp gối bên phải");
        lines.add("Mô tả chấn thương: Sưng gối");
        lines.add("Hướng điều trị: Chăm sóc tại nhà");
        lines.add("Xét nghiệm: —");
        for (int i = 0; i < 80; i++) lines.add("Lời dặn: Tái khám theo chỉ định của bác sĩ.");
        lines.add("Bác sĩ: Trần Thị Hương");

        byte[] bytes = new ResultSheetPdfRenderer().render(
                new ResultSheetPdfRenderer.Snapshot("Nguyễn Thị Ánh", lines));
        try (var document = Loader.loadPDF(bytes)) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(document.getNumberOfPages() > 1);
            assertTrue(text.contains("PHIẾU KẾT QUẢ KHÁM BỆNH"), text);
            assertTrue(text.contains("Nguyễn Thị Ánh"), text);
            assertTrue(text.contains("Đau khớp gối bên phải"), text);
            assertTrue(text.contains("Xét nghiệm: —"), text);
            assertTrue(text.contains("Mô tả chấn thương: Sưng gối"), text);
            assertTrue(text.contains("Hướng điều trị: Chăm sóc tại nhà"), text);
            assertTrue(text.contains("Trần Thị Hương"), text);
        }
    }
}
