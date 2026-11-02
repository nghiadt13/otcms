package fu.fa26.sep490.g58.otcms_be.reporting.application;
import java.util.Map;
public interface ReportPdfWriter {
    byte[] write(Map<String, Object> report);
}
