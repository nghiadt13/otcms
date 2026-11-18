package fu.fa26.sep490.g58.otcms_be.reporting.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
    public record MetricValueResponse(long value, Long previousValue) {
    }
    public record StatusMetricResponse(String code, long value, Long previousValue) {
    }
    public record ServiceComparisonResponse(String serviceId, String name, String category, long value,
        Long previousValue) {
    }
    public record CountsResponse(String status, MetricValueResponse total, List<StatusMetricResponse> byStatus) {
    }
    public record RoomWaitingResponse(String roomId, String roomName, long value, Long previousValue) {
    }
    public record VisitsResponse(String status, MetricValueResponse total, List<StatusMetricResponse> byStatus,
        List<RoomWaitingResponse> waitingByRoom, String waitingByRoomStatus) {
    }
    public record ServicesResponse(String status, List<ServiceComparisonResponse> top) {
    }
    /** Laboratory has no source yet (owned by another team): {@code labStatus} stays NO_SOURCE and labPerformed is 0. */
    public record ImagingAndLabResponse(String status, MetricValueResponse imagingPerformed,
        MetricValueResponse labPerformed, String labStatus) {
    }
    public record PaymentsResponse(String status, MetricValueResponse collected, List<StatusMetricResponse> byMethod) {
    }
    public record PendingAndCancelledResponse(String status, MetricValueResponse pendingPayment,
        MetricValueResponse cancelled, MetricValueResponse refundPending) {
    }
    public record RatingMetricResponse(Double value, Double previousValue) {
    }
    public record ReviewsResponse(String status, RatingMetricResponse averageRating, MetricValueResponse pendingApproval) {
    }
    public record DashboardResponse(PeriodResponse period, PeriodResponse previousPeriod,
        CountsResponse appointments, VisitsResponse visits, ServicesResponse services,
        ImagingAndLabResponse imagingAndLab, PaymentsResponse payments,
        PendingAndCancelledResponse pendingAndCancelled, ReviewsResponse reviews) {
    }
    public record SubjectResponse(String code, String label, List<String> breakdownOptions) {
    }
    public record SubjectsResponse(List<SubjectResponse> items) {
    }
    public record ReportQueryResponse(String subject, String from, String to,
        @JsonInclude(JsonInclude.Include.NON_NULL) String breakdown) {
    }
    /** {@code kind} is one of text, date, count, money, score. */
    public record ColumnResponse(String key, String label, String kind) {
    }
    /** One table row; each subtype matches the column keys of its topic (typed on purpose: DTOs never carry Map or Object). */
    public sealed interface RowResponse permits DailyRowResponse, ServiceRowResponse, PaymentDateRowResponse,
        PaymentReceptionistRowResponse, PaymentMethodRowResponse, OrderGroupRowResponse, ReviewDayRowResponse {
    }
    public record DailyRowResponse(String date, long count) implements RowResponse {
    }
    public record ServiceRowResponse(String name, long count) implements RowResponse {
    }
    public record PaymentDateRowResponse(String date, long count, long amount) implements RowResponse {
    }
    public record PaymentReceptionistRowResponse(String receptionist, long count, long amount) implements RowResponse {
    }
    public record PaymentMethodRowResponse(String method, long count, long amount) implements RowResponse {
    }
    public record OrderGroupRowResponse(String group, long count, long amount) implements RowResponse {
    }
    public record ReviewDayRowResponse(String date, long count, Double average) implements RowResponse {
    }
    public record TotalRowResponse(long count, @JsonInclude(JsonInclude.Include.NON_NULL) Long amount) {
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
