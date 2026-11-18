package fu.fa26.sep490.g58.otcms_be.clinical.service;

import java.util.List;
import java.util.Locale;

public final class Icd10Catalog {
    public record Entry(String code, String name, String group) { }

    private static final List<Entry> ENTRIES = List.of(
            new Entry("M06.9", "Viêm khớp dạng thấp, không đặc hiệu", "M"),
            new Entry("M10.9", "Gút, không đặc hiệu", "M"),
            new Entry("M16.1", "Thoái hóa khớp háng nguyên phát", "M"),
            new Entry("M17.0", "Thoái hóa khớp gối nguyên phát hai bên", "M"),
            new Entry("M17.1", "Thoái hóa khớp gối nguyên phát", "M"),
            new Entry("M17.9", "Thoái hóa khớp gối, không đặc hiệu", "M"),
            new Entry("M19.9", "Thoái hóa khớp, không đặc hiệu", "M"),
            new Entry("M20.1", "Ngón chân cái vẹo ngoài", "M"),
            new Entry("M23.2", "Rối loạn sụn chêm do rách hoặc chấn thương cũ", "M"),
            new Entry("M23.3", "Rối loạn sụn chêm khác", "M"),
            new Entry("M24.2", "Rối loạn dây chằng", "M"),
            new Entry("M25.5", "Đau khớp", "M"),
            new Entry("M25.6", "Cứng khớp, chưa phân loại", "M"),
            new Entry("M43.1", "Trượt đốt sống", "M"),
            new Entry("M45", "Viêm cột sống dính khớp", "M"),
            new Entry("M47.8", "Thoái hóa cột sống khác", "M"),
            new Entry("M47.9", "Thoái hóa cột sống, không đặc hiệu", "M"),
            new Entry("M48.0", "Hẹp ống sống", "M"),
            new Entry("M50.1", "Rối loạn đĩa đệm cổ có bệnh rễ thần kinh", "M"),
            new Entry("M51.1", "Rối loạn đĩa đệm thắt lưng có bệnh rễ thần kinh", "M"),
            new Entry("M51.2", "Thoát vị đĩa đệm thắt lưng khác", "M"),
            new Entry("M54.2", "Đau cổ", "M"),
            new Entry("M54.3", "Đau thần kinh tọa", "M"),
            new Entry("M54.4", "Đau thắt lưng kèm đau thần kinh tọa", "M"),
            new Entry("M54.5", "Đau thắt lưng", "M"),
            new Entry("M65.3", "Ngón tay lò xo", "M"),
            new Entry("M65.4", "Viêm bao gân quay (De Quervain)", "M"),
            new Entry("M75.0", "Viêm dính bao khớp vai", "M"),
            new Entry("M75.1", "Hội chứng chóp xoay", "M"),
            new Entry("M75.4", "Hội chứng chèn ép khớp vai", "M"),
            new Entry("M76.6", "Viêm gân Achilles", "M"),
            new Entry("M77.1", "Viêm lồi cầu ngoài xương cánh tay", "M"),
            new Entry("M79.1", "Đau cơ", "M"),
            new Entry("M80.0", "Loãng xương kèm gãy xương bệnh lý", "M"),
            new Entry("M81.0", "Loãng xương sau mãn kinh", "M"),
            new Entry("M84.4", "Gãy xương bệnh lý", "M"),
            new Entry("S13.4", "Bong gân cột sống cổ", "S"),
            new Entry("S22.3", "Gãy xương sườn", "S"),
            new Entry("S32.0", "Gãy đốt sống thắt lưng", "S"),
            new Entry("S33.5", "Bong gân cột sống thắt lưng", "S"),
            new Entry("S42.0", "Gãy xương đòn", "S"),
            new Entry("S42.2", "Gãy đầu trên xương cánh tay", "S"),
            new Entry("S43.0", "Trật khớp vai", "S"),
            new Entry("S46.0", "Rách chóp xoay khớp vai", "S"),
            new Entry("S52.5", "Gãy đầu dưới xương quay", "S"),
            new Entry("S52.6", "Gãy đầu dưới xương quay và xương trụ", "S"),
            new Entry("S62.0", "Gãy xương thuyền bàn tay", "S"),
            new Entry("S62.3", "Gãy xương đốt bàn tay", "S"),
            new Entry("S72.0", "Gãy cổ xương đùi", "S"),
            new Entry("S72.1", "Gãy xương đùi vùng mấu chuyển", "S"),
            new Entry("S72.3", "Gãy thân xương đùi", "S"),
            new Entry("S76.1", "Tổn thương cơ và gân tứ đầu đùi", "S"),
            new Entry("S82.0", "Gãy xương bánh chè", "S"),
            new Entry("S82.1", "Gãy đầu trên xương chày", "S"),
            new Entry("S82.6", "Gãy mắt cá ngoài", "S"),
            new Entry("S82.8", "Gãy xương cẳng chân khác", "S"),
            new Entry("S83.2", "Rách sụn chêm do chấn thương", "S"),
            new Entry("S83.4", "Bong gân dây chằng bên khớp gối", "S"),
            new Entry("S83.5", "Tổn thương dây chằng chéo khớp gối", "S"),
            new Entry("S86.0", "Tổn thương gân Achilles", "S"),
            new Entry("S92.3", "Gãy xương đốt bàn chân", "S"),
            new Entry("S93.4", "Bong gân cổ chân", "S")
    );

    private Icd10Catalog() { }

    public static List<Entry> entries() { return ENTRIES; }

    public static boolean contains(String code) {
        return code != null && ENTRIES.stream().anyMatch(entry -> entry.code().equals(code.toUpperCase(Locale.ROOT)));
    }

    /** Tên trong danh mục của mã (không phân biệt hoa thường); chuỗi rỗng khi mã ngoài danh mục. */
    public static String nameOf(String code) {
        if (code == null) return "";
        String key = code.toUpperCase(Locale.ROOT);
        return ENTRIES.stream().filter(entry -> entry.code().equals(key)).map(Entry::name).findFirst().orElse("");
    }
}
