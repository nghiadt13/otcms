package fu.fa26.sep490.g58.otcms_be.reporting.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import fu.fa26.sep490.g58.otcms_be.reporting.model.MetricValue;
import fu.fa26.sep490.g58.otcms_be.reporting.model.ServiceComparison;
import fu.fa26.sep490.g58.otcms_be.reporting.model.StatusMetric;
import java.time.Instant;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public final class ReportingDtos {
    private ReportingDtos() {
    }
    public record CreateReportRequest(@NotNull String subject, @NotNull String from, @NotNull String to, String breakdown) {
    }
    public record PeriodResponse(String from, String to) {
    }
    public record UnavailableSourceResponse(String status) {
    }
    public record CountsResponse(String status, MetricValue total, List<StatusMetric> byStatus) {
    }
    public record RoomWaitingResponse(String roomId, String roomName, long value, Long previousValue) {
    }
    public record VisitsResponse(String status, MetricValue total, List<StatusMetric> byStatus,
        List<RoomWaitingResponse> waitingByRoom, String waitingByRoomStatus) {
    }
    public record ServicesResponse(String status, List<ServiceComparison> top) {
    }
    public record DashboardResponse(PeriodResponse period, PeriodResponse previousPeriod,
        CountsResponse appointments, VisitsResponse visits, ServicesResponse services,
        UnavailableSourceResponse imagingAndLab, UnavailableSourceResponse payments,
        UnavailableSourceResponse pendingAndCancelled, UnavailableSourceResponse reviews) {
    }
    public record SubjectResponse(String code, String label, List<String> breakdownOptions) {
    }
    public record SubjectsResponse(List<SubjectResponse> items) {
    }
    public record ReportQueryResponse(String subject, String from, String to,
        @JsonInclude(JsonInclude.Include.NON_NULL) String breakdown) {
    }
    public record ColumnResponse(String key, String label, String kind) {
    }
    public sealed interface RowResponse permits DailyRowResponse, ServiceRowResponse {
    }
    public record DailyRowResponse(String date, long count) implements RowResponse {
    }
    public record ServiceRowResponse(String name, long count) implements RowResponse {
    }
    public record TotalRowResponse(long count) {
    }
    public record SectionResponse(
        String title,
        List<ColumnResponse> columns,
        List<RowResponse> rows,
        TotalRowResponse totalRow
    ) {
    }
    public record ReportResponse(ReportQueryResponse query, boolean hasData, Instant generatedAt,
        List<String> allowedActions, List<SectionResponse> sections) {
    }
}
