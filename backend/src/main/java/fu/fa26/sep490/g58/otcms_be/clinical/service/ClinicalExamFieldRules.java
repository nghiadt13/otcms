package fu.fa26.sep490.g58.otcms_be.clinical.service;

import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Quy tắc cho trường khám chỉnh hình: chấn thương (UC-37, BR-39: vị trí, bên, cơ chế, mô tả) và hướng điều trị (UC-42, BR-40).
 * Bản nháp được lưu khi còn thiếu trường. Khi bác sĩ hoàn thành khám hoặc sửa kết quả thì vị trí, bên, cơ chế, mô tả
 * và hướng điều trị đều bắt buộc; vi phạm trả MSG-39.
 */
public final class ClinicalExamFieldRules {
    /** Mã bên chấn thương (cột injury_side, CHECK V136) và nhãn tiếng Việt theo thứ tự hiển thị trên form. */
    private static final Map<String, String> SIDE_LABELS = labels(
            "LEFT", "Trái",
            "RIGHT", "Phải",
            "BOTH", "Hai bên",
            "NOT_APPLICABLE", "Không áp dụng");
    /** Bốn hướng điều trị của BR-40 và nhãn tiếng Việt. */
    private static final Map<String, String> DIRECTION_LABELS = labels(
            "HOME_CARE", "Chăm sóc tại nhà",
            "MEDICATION", "Dùng thuốc",
            "PHYSICAL_THERAPY", "Vật lý trị liệu",
            "PROCEDURE_ELSEWHERE", "Thủ thuật hoặc phẫu thuật tại cơ sở khác");
    /** injury_body_site là VARCHAR(120); mô tả và cơ chế là TEXT nên giới hạn được kiểm ở đây. */
    private static final int BODY_SITE_MAX = 120;
    private static final int MECHANISM_MAX = 1000;
    private static final int DESCRIPTION_MAX = 2000;

    private ClinicalExamFieldRules() { }

    /** Kiểm tra dữ liệu gửi lên; required = true khi bác sĩ hoàn thành khám hoặc sửa kết quả. */
    public static void validate(String bodySite, String side, String mechanism, String description,
            List<String> directions, boolean required) {
        String site = trimToNull(bodySite);
        String code = trimToNull(side);
        String cause = trimToNull(mechanism);
        String detail = trimToNull(description);
        if (required && (site == null || !isSide(code) || cause == null || detail == null
                || directions == null || directions.isEmpty())) {
            throw invalid();
        }
        if ((code != null && !isSide(code)) || tooLong(site, BODY_SITE_MAX)
                || tooLong(cause, MECHANISM_MAX) || tooLong(detail, DESCRIPTION_MAX)) {
            throw invalid();
        }
        if (directions != null && (directions.stream().anyMatch(d -> d == null || !DIRECTION_LABELS.containsKey(d))
                || new HashSet<>(directions).size() != directions.size())) {
            throw invalid();
        }
    }

    /** Cắt khoảng trắng; chuỗi rỗng thành null. */
    public static String trimToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    /** Hướng điều trị để lưu: danh sách rỗng thành null vì cột chỉ nhận từ 1 đến 4 phần tử. */
    public static List<String> normalizeDirections(List<String> directions) {
        return directions == null || directions.isEmpty() ? null : List.copyOf(directions);
    }

    /** Nhãn tiếng Việt của mã bên; null khi chưa chọn. */
    public static String sideLabel(String side) {
        return side == null ? null : SIDE_LABELS.get(side);
    }

    /** Các hướng điều trị nối bằng dấu phẩy cho NF-02 và kết quả bệnh nhân; chuỗi rỗng khi chưa chọn. */
    public static String directionText(List<String> directions) {
        if (directions == null || directions.isEmpty()) return "";
        return directions.stream().map(DIRECTION_LABELS::get).collect(Collectors.joining(", "));
    }

    private static boolean isSide(String side) {
        return side != null && SIDE_LABELS.containsKey(side);
    }

    private static boolean tooLong(String text, int max) {
        return text != null && text.length() > max;
    }

    private static ClinicalOperationException invalid() {
        return new ClinicalOperationException("MSG-39");
    }

    private static Map<String, String> labels(String... pairs) {
        var labels = new LinkedHashMap<String, String>();
        for (int i = 0; i < pairs.length; i += 2) labels.put(pairs[i], pairs[i + 1]);
        return Collections.unmodifiableMap(labels);
    }
}
