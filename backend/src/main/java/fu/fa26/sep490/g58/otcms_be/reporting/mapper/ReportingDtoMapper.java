package fu.fa26.sep490.g58.otcms_be.reporting.mapper;

import fu.fa26.sep490.g58.otcms_be.reporting.model.DashboardSummary;
import fu.fa26.sep490.g58.otcms_be.reporting.model.ReportResult;
import fu.fa26.sep490.g58.otcms_be.reporting.model.ReportingPeriod;
import fu.fa26.sep490.g58.otcms_be.reporting.model.dto.ReportingDtos.MetricValueResponse;
import fu.fa26.sep490.g58.otcms_be.reporting.model.dto.ReportingDtos.StatusMetricResponse;
import fu.fa26.sep490.g58.otcms_be.reporting.model.dto.ReportingDtos.ServiceComparisonResponse;
import fu.fa26.sep490.g58.otcms_be.reporting.model.dto.ReportingDtos.ColumnResponse;
import fu.fa26.sep490.g58.otcms_be.reporting.model.dto.ReportingDtos.CountsResponse;
import fu.fa26.sep490.g58.otcms_be.reporting.model.dto.ReportingDtos.DailyRowResponse;
import fu.fa26.sep490.g58.otcms_be.reporting.model.dto.ReportingDtos.DashboardResponse;
import fu.fa26.sep490.g58.otcms_be.reporting.model.dto.ReportingDtos.PeriodResponse;
import fu.fa26.sep490.g58.otcms_be.reporting.model.dto.ReportingDtos.ReportQueryResponse;
import fu.fa26.sep490.g58.otcms_be.reporting.model.dto.ReportingDtos.ReportResponse;
import fu.fa26.sep490.g58.otcms_be.reporting.model.dto.ReportingDtos.RowResponse;
import fu.fa26.sep490.g58.otcms_be.reporting.model.dto.ReportingDtos.SectionResponse;
import fu.fa26.sep490.g58.otcms_be.reporting.model.dto.ReportingDtos.ServiceRowResponse;
import fu.fa26.sep490.g58.otcms_be.reporting.model.dto.ReportingDtos.ServicesResponse;
import fu.fa26.sep490.g58.otcms_be.reporting.model.dto.ReportingDtos.SubjectResponse;
import fu.fa26.sep490.g58.otcms_be.reporting.model.dto.ReportingDtos.SubjectsResponse;
import fu.fa26.sep490.g58.otcms_be.reporting.model.dto.ReportingDtos.TotalRowResponse;
import fu.fa26.sep490.g58.otcms_be.reporting.model.dto.ReportingDtos.UnavailableSourceResponse;
import fu.fa26.sep490.g58.otcms_be.reporting.model.dto.ReportingDtos.VisitsResponse;
import java.util.List;

/** Fixed presentation metadata belongs here, never in query orchestration. */
public final class ReportingDtoMapper {
    private ReportingDtoMapper() {
    }
    private static final List<ColumnResponse> DAILY_COLUMNS = List.of(
        new ColumnResponse("date", "Ngày", "date"), new ColumnResponse("count", "Số lượng", "count"));
    private static final List<ColumnResponse> SERVICE_COLUMNS = List.of(
        new ColumnResponse("name", "Dịch vụ", "text"), new ColumnResponse("count", "Lịch hẹn đã hoàn tất", "count"));
    private static PeriodResponse period(ReportingPeriod period) {
        return new PeriodResponse(period.from().toString(), period.to().toString());
    }
    public static DashboardResponse dashboard(DashboardSummary summary) {
        var unavailable = new UnavailableSourceResponse("NO_SOURCE");
        return new DashboardResponse(period(summary.period()), period(summary.previousPeriod()),
            new CountsResponse("READY", metric(summary.appointments().total()),
                summary.appointments().byStatus().stream().map(ReportingDtoMapper::statusMetric).toList()),
            new VisitsResponse("READY", metric(summary.visits().total()),
                summary.visits().byStatus().stream().map(ReportingDtoMapper::statusMetric).toList(), List.of(), "NO_SOURCE"),
            new ServicesResponse("READY", summary.services().stream().map(item -> new ServiceComparisonResponse(
                item.serviceId(), item.name(), item.category(), item.value(), item.previousValue())).toList()),
            unavailable, unavailable, unavailable, unavailable);
    }
    private static MetricValueResponse metric(fu.fa26.sep490.g58.otcms_be.reporting.model.MetricValue value) {
        return new MetricValueResponse(value.value(), value.previousValue());
    }
    private static StatusMetricResponse statusMetric(fu.fa26.sep490.g58.otcms_be.reporting.model.StatusMetric value) {
        return new StatusMetricResponse(value.code(), value.value(), value.previousValue());
    }
    public static SubjectsResponse subjects(List<String> codes) {
        return new SubjectsResponse(codes.stream().map(code -> new SubjectResponse(code, switch (code) {
                case "APPOINTMENTS" -> "Lịch hẹn theo ngày";
                case "VISITS" -> "Lượt khám theo ngày";
                default -> "Dịch vụ khám theo lịch hẹn đã hoàn tất";
        }, code.equals("SERVICES") ? List.of() : List.of("DATE"))).toList());
    }
    public static ReportResponse report(ReportResult result) {
        boolean services = result.subject().equals("SERVICES");
        List<RowResponse> rows = services
        ? result.services().stream().<RowResponse>map(row -> new ServiceRowResponse(row.name(), row.value())).toList()
            : result.daily().stream().<RowResponse>map(row -> new DailyRowResponse(row.date(), row.count())).toList();
        String breakdown = result.breakdown() == null || result.breakdown().isEmpty() ? null : result.breakdown();
        var query = new ReportQueryResponse(result.subject(), result.period().from().toString(),
            result.period().to().toString(), breakdown);
        var section = new SectionResponse(services ? "Dịch vụ khám từ lịch đã hoàn tất" : "Thống kê theo ngày",
            services ? SERVICE_COLUMNS : DAILY_COLUMNS, rows, new TotalRowResponse(result.total()));
        return new ReportResponse(query, result.total() > 0, result.generatedAt(), List.of(), List.of(section));
    }
}
