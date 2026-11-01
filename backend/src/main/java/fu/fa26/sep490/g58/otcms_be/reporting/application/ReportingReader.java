package fu.fa26.sep490.g58.otcms_be.reporting.application;
import fu.fa26.sep490.g58.otcms_be.reporting.domain.ReportingPeriod;
import java.util.List;
import java.util.Map;
public interface ReportingReader {
    Map<String,Long> appointmentCounts(ReportingPeriod period);
    Map<String,Long> visitCounts(ReportingPeriod period);
    List<Map<String,Object>> daily(String subject,ReportingPeriod period);
    List<Map<String,Object>> services(ReportingPeriod period);
}
