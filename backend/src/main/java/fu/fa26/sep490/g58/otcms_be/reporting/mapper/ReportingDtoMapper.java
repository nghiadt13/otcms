package fu.fa26.sep490.g58.otcms_be.reporting.mapper;

import fu.fa26.sep490.g58.otcms_be.reporting.model.DashboardSummary;
import fu.fa26.sep490.g58.otcms_be.reporting.model.MetricValue;
import fu.fa26.sep490.g58.otcms_be.reporting.model.ReportResult;
import fu.fa26.sep490.g58.otcms_be.reporting.model.ReportSubjects;
import fu.fa26.sep490.g58.otcms_be.reporting.model.ReportingPeriod;
import fu.fa26.sep490.g58.otcms_be.reporting.model.StatusMetric;
import fu.fa26.sep490.g58.otcms_be.reporting.model.dto.ReportingDtos.*;
import java.util.List;
import java.util.Map;

/** Fixed presentation metadata belongs here, never in query orchestration. */
public final class ReportingDtoMapper {
    private ReportingDtoMapper() {
    }
    private static final ColumnResponse DATE = new ColumnResponse("date", "Ngày", "date");
    private static final ColumnResponse COUNT = new ColumnResponse("count", "Số lượng", "count");
    private static final ColumnResponse AMOUNT = new ColumnResponse("amount", "Số tiền (VND)", "money");
    private static final Map<String, String> METHOD_LABELS = Map.of("CASH", "Tiền mặt", "PAYOS", "PayOS (trực tuyến)");
    private static final Map<String, String> GROUP_LABELS = Map.of("PENDING_PAYMENT", "Chờ thanh toán",
        "CANCELLED", "Đã hủy", "REFUND_PENDING", "Chờ hoàn tiền");
    private static final String ONLINE_RECEPTIONIST = "Thanh toán trực tuyến (không qua lễ tân)";
    private static PeriodResponse period(ReportingPeriod period) {
        return new PeriodResponse(period.from().toString(), period.to().toString());
    }
    public static DashboardResponse dashboard(DashboardSummary summary) {
        var orders = summary.orders();
        var reviews = summary.reviews();
        return new DashboardResponse(period(summary.period()), period(summary.previousPeriod()),
            new CountsResponse("READY", metric(summary.appointments().total()),
                summary.appointments().byStatus().stream().map(ReportingDtoMapper::statusMetric).toList()),
            new VisitsResponse("READY", metric(summary.visits().total()),
                summary.visits().byStatus().stream().map(ReportingDtoMapper::statusMetric).toList(),
                summary.waitingByRoom().stream().map(room -> new RoomWaitingResponse(room.roomId(), room.roomName(),
                    room.value(), null)).toList(), "READY"),
            new ServicesResponse("READY", summary.services().stream().map(item -> new ServiceComparisonResponse(
                item.serviceId(), item.name(), item.category(), item.value(), item.previousValue())).toList()),
            new ImagingAndLabResponse("READY", metric(summary.imagingPerformed()), new MetricValueResponse(0, 0L), "NO_SOURCE"),
            new PaymentsResponse("READY", metric(summary.payments().collected()),
                summary.payments().byMethod().stream().map(ReportingDtoMapper::statusMetric).toList()),
            new PendingAndCancelledResponse("READY", metric(orders.pendingPayment()), metric(orders.cancelled()),
                new MetricValueResponse(orders.refundPending(), null)),
            new ReviewsResponse("READY", new RatingMetricResponse(round(reviews.average()), round(reviews.previousAverage())),
                new MetricValueResponse(reviews.pendingApproval(), null)));
    }
    private static Double round(Double value) {
        return value == null ? null : Math.round(value * 10.0) / 10.0;
    }
    private static MetricValueResponse metric(MetricValue value) {
        return new MetricValueResponse(value.value(), value.previousValue());
    }
    private static StatusMetricResponse statusMetric(StatusMetric value) {
        return new StatusMetricResponse(value.code(), value.value(), value.previousValue());
    }
    public static SubjectsResponse subjects(List<String> codes) {
        return new SubjectsResponse(codes.stream().map(code -> new SubjectResponse(code, switch (code) {
                case "APPOINTMENTS" -> "Lịch hẹn theo ngày";
                case "VISITS" -> "Lượt khám theo ngày";
                case "SERVICES" -> "Dịch vụ khám theo lịch hẹn đã hoàn tất";
                case "IMAGING" -> "Chẩn đoán hình ảnh đã thực hiện theo ngày";
                case "PAYMENTS" -> "Thanh toán đã thu";
                case "PENDING_CANCELLED_ORDERS" -> "Chỉ định chờ thanh toán, đã hủy và chờ hoàn tiền";
                default -> "Đánh giá của bệnh nhân";
        }, ReportSubjects.breakdowns(code))).toList());
    }
    public static ReportResponse report(ReportResult result) {
        String breakdown = result.breakdown() == null || result.breakdown().isEmpty() ? null : result.breakdown();
        var query = new ReportQueryResponse(result.subject(), result.period().from().toString(),
            result.period().to().toString(), breakdown);
        var section = new SectionResponse(title(result), columns(result), rows(result),
            new TotalRowResponse(result.total(), result.totalAmount()));
        return new ReportResponse(query, result.total() > 0, result.generatedAt(), List.of("export"), List.of(section));
    }
    private static String title(ReportResult result) {
        return switch (result.subject()) {
            case "SERVICES" -> "Dịch vụ khám từ lịch đã hoàn tất";
            case "IMAGING" -> "Chẩn đoán hình ảnh đã thực hiện theo ngày";
            case "PAYMENTS" -> "Thanh toán đã thu theo " + switch (result.breakdown()) {
                case "RECEPTIONIST" -> "lễ tân";
                case "METHOD" -> "phương thức";
                default -> "ngày";
            };
            case "PENDING_CANCELLED_ORDERS" -> "Chỉ định chờ thanh toán, đã hủy và chờ hoàn tiền";
            case "REVIEWS" -> "Đánh giá đã duyệt theo ngày";
            default -> "Thống kê theo ngày";
        };
    }
    private static List<ColumnResponse> columns(ReportResult result) {
        return switch (result.subject()) {
            case "SERVICES" -> List.of(new ColumnResponse("name", "Dịch vụ", "text"),
                new ColumnResponse("count", "Lịch hẹn đã hoàn tất", "count"));
            case "PAYMENTS" -> List.of(switch (result.breakdown()) {
                case "RECEPTIONIST" -> new ColumnResponse("receptionist", "Lễ tân", "text");
                case "METHOD" -> new ColumnResponse("method", "Phương thức", "text");
                default -> DATE;
            }, new ColumnResponse("count", "Số giao dịch", "count"), AMOUNT);
            case "PENDING_CANCELLED_ORDERS" -> List.of(new ColumnResponse("group", "Nhóm", "text"),
                new ColumnResponse("count", "Số khoản", "count"), AMOUNT);
            case "REVIEWS" -> List.of(DATE, new ColumnResponse("count", "Số đánh giá", "count"),
                new ColumnResponse("average", "Điểm trung bình", "score"));
            default -> List.of(DATE, COUNT);
        };
    }
    private static List<RowResponse> rows(ReportResult result) {
        return switch (result.subject()) {
            case "SERVICES" -> result.services().stream().<RowResponse>map(row -> new ServiceRowResponse(row.name(), row.value())).toList();
            case "PAYMENTS" -> result.rows().stream().<RowResponse>map(row -> switch (result.breakdown()) {
                case "RECEPTIONIST" -> new PaymentReceptionistRowResponse(row.label() == null ? ONLINE_RECEPTIONIST : row.label(),
                    row.count(), row.amount());
                case "METHOD" -> new PaymentMethodRowResponse(METHOD_LABELS.getOrDefault(row.label(), row.label()), row.count(), row.amount());
                default -> new PaymentDateRowResponse(row.label(), row.count(), row.amount());
            }).toList();
            case "PENDING_CANCELLED_ORDERS" -> result.rows().stream().<RowResponse>map(row ->
                new OrderGroupRowResponse(GROUP_LABELS.getOrDefault(row.label(), row.label()), row.count(), row.amount())).toList();
            case "REVIEWS" -> result.rows().stream().<RowResponse>map(row ->
                new ReviewDayRowResponse(row.label(), row.count(), row.average())).toList();
            default -> result.daily().stream().<RowResponse>map(row -> new DailyRowResponse(row.date(), row.count())).toList();
        };
    }
}
