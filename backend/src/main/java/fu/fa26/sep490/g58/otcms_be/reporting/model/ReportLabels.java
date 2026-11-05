package fu.fa26.sep490.g58.otcms_be.reporting.model;

/** Shared fixed labels for the web projection and PDF presentation. */
public final class ReportLabels {
    public static final String DATE = "Ngày";
    public static final String COUNT = "Số lượng";
    public static final String SERVICE = "Dịch vụ";
    public static final String COMPLETED = "Lịch hẹn đã hoàn tất";

    private ReportLabels() {
    }

    public static String subject(String code) {
        return switch (code) {
            case "APPOINTMENTS" -> "Lịch hẹn theo ngày";
            case "VISITS" -> "Lượt khám theo ngày";
            case "SERVICES" -> "Dịch vụ khám từ lịch hẹn đã hoàn tất";
            default -> throw new IllegalArgumentException("Unsupported validated report subject");
        };
    }
}
