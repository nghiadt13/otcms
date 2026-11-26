package fu.fa26.sep490.g58.otcms_be.reporting.repository;

import fu.fa26.sep490.g58.otcms_be.reporting.model.DailyCount;
import fu.fa26.sep490.g58.otcms_be.reporting.model.DashboardSections.ReviewStats;
import fu.fa26.sep490.g58.otcms_be.reporting.model.DashboardSections.RoomWaiting;
import fu.fa26.sep490.g58.otcms_be.reporting.model.ReportRow;
import fu.fa26.sep490.g58.otcms_be.reporting.model.ReportingPeriod;
import fu.fa26.sep490.g58.otcms_be.reporting.model.ServiceCount;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

/** Native JPA read model for cross-feature dashboard aggregates. */
@Repository
@Primary
public class ReportingQueryOperationsImpl implements ReportingRepository {
    private static final String VN = "Asia/Ho_Chi_Minh";
    private static final String ORDER_FROM = "service_orders o JOIN service_order_rounds r ON r.id=o.ordering_round_id "
            + "LEFT JOIN imaging_executions e ON e.service_order_id=o.id";
    private static final String IMAGING_FROM = "service_orders o JOIN imaging_executions e ON e.service_order_id=o.id";
    private static final String IMAGING_TIME = "COALESCE(e.first_use_at, e.updated_at)";
    private static final String IMAGING_DONE = "o.service_type='IMAGING' AND e.execution_status IN ('RESULT_AVAILABLE','REVIEWED')";
    private static final String METHOD = "CASE WHEN pa.channel='COUNTER' THEN 'CASH' ELSE 'PAYOS' END";

    @PersistenceContext private EntityManager entityManager;

    @Override public Map<String, Long> appointmentCounts(ReportingPeriod p) {
        return counts("SELECT CASE WHEN status='CONFIRMED' THEN 'BOOKED' WHEN status IN ('IN_PROGRESS','COMPLETED') "
                + "THEN 'CHECKED_IN' ELSE status END code,count(*) n FROM appointments WHERE start_time>=:from "
                + "AND start_time<:until AND status NOT IN ('AWAITING_PAYMENT','PAYMENT_EXPIRED') GROUP BY 1", p);
    }

    @Override public Map<String, Long> visitCounts(ReportingPeriod p) {
        return counts("SELECT CASE WHEN v.status='CLOSED' THEN 'COMPLETED' WHEN v.status='CANCELLED' THEN 'CANCELLED' "
                + "WHEN EXISTS(SELECT 1 FROM visit_assignments a WHERE a.visit_id=v.id AND a.status='IN_PROGRESS' "
                + "AND a.actual_ended_at IS NULL) THEN 'IN_PROGRESS' ELSE 'WAITING' END code,count(*) n "
                + "FROM visits v WHERE v.checked_in_at>=:from AND v.checked_in_at<:until GROUP BY 1", p);
    }

    @Override public List<DailyCount> daily(String subject, ReportingPeriod p) {
        String table = switch (subject) { case "APPOINTMENTS" -> "appointments"; case "IMAGING" -> IMAGING_FROM; default -> "visits"; };
        String column = switch (subject) { case "APPOINTMENTS" -> "start_time"; case "IMAGING" -> IMAGING_TIME; default -> "checked_in_at"; };
        String filter = switch (subject) { case "APPOINTMENTS" -> " AND status NOT IN ('AWAITING_PAYMENT','PAYMENT_EXPIRED')";
            case "IMAGING" -> " AND " + IMAGING_DONE; default -> ""; };
        String sql = "SELECT to_char(" + column + " AT TIME ZONE '" + VN + "','YYYY-MM-DD') report_date,count(*) n FROM "
                + table + " WHERE " + column + ">=:from AND " + column + "<:until" + filter + " GROUP BY 1 ORDER BY 1";
        return periodRows(sql, p).stream().map(row -> new DailyCount((String) row[0], number(row[1]).longValue())).toList();
    }

    @Override public List<ServiceCount> services(ReportingPeriod p) {
        return periodRows("SELECT a.service_id,count(*) n FROM appointments a WHERE a.status='COMPLETED' "
                + "AND a.start_time>=:from AND a.start_time<:until GROUP BY a.service_id ORDER BY n DESC,a.service_id", p)
                .stream().map(row -> new ServiceCount(row[0].toString(), number(row[1]).longValue())).toList();
    }

    @Override public long imagingPerformed(ReportingPeriod p) {
        return number(single("SELECT count(*) FROM " + IMAGING_FROM + " WHERE " + IMAGING_DONE + " AND "
                + IMAGING_TIME + ">=:from AND " + IMAGING_TIME + "<:until", p)).longValue();
    }

    @Override public Map<String, Long> paymentsByMethod(ReportingPeriod p) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Object[] row : periodRows("SELECT " + METHOD + " code,COALESCE(sum(pa.amount),0) n FROM payment_attempts pa "
                + "WHERE pa.status='CONFIRMED' AND pa.confirmed_at>=:from AND pa.confirmed_at<:until GROUP BY 1", p))
            result.put((String) row[0], number(row[1]).longValue());
        return result;
    }

    @Override public Map<String, Long> orderCounts(ReportingPeriod p) {
        return counts("SELECT CASE WHEN o.order_status='CANCELLED' OR e.execution_status='CANCELLED' THEN 'CANCELLED' "
                + "ELSE 'PENDING_PAYMENT' END code,count(*) n FROM " + ORDER_FROM + " WHERE r.ordered_at>=:from "
                + "AND r.ordered_at<:until AND (o.order_status='CANCELLED' OR e.execution_status='CANCELLED' "
                + "OR o.payment_status='PENDING_PAYMENT') GROUP BY 1", p);
    }

    @Override public long refundPendingItems() {
        return number(single("SELECT count(*) FROM invoice_items WHERE refund_status='REFUND_PENDING'", null)).longValue();
    }

    @Override public ReviewStats reviewStats(ReportingPeriod p) {
        Object[] row = (Object[]) single("SELECT count(*) n,avg(rating) mean FROM patient_reviews WHERE status='PUBLISHED' "
                + "AND submitted_at>=:from AND submitted_at<:until", p);
        return new ReviewStats(number(row[0]).longValue(), row[1] == null ? null : number(row[1]).doubleValue());
    }

    @Override public long pendingReviews() {
        return number(single("SELECT count(*) FROM patient_reviews WHERE status='PENDING'", null)).longValue();
    }

    @Override public List<RoomWaiting> waitingByRoom(LocalDate today) {
        Query query = entityManager.createNativeQuery("SELECT r.id,r.display_label,count(*) n FROM queue_entries q JOIN rooms r ON r.id=q.room_id "
                        + "WHERE q.stage IN ('EXAM','SERVICE') AND q.status IN ('WAITING','CALLED') "
                        + "AND (q.stage='SERVICE' OR q.clinic_date=:today) GROUP BY r.id,r.display_label ORDER BY n DESC,r.display_label,r.id")
                .setParameter("today", today);
        return queryRows(query).stream()
                .map(row -> new RoomWaiting(row[0].toString(), row[1].toString(), number(row[2]).longValue())).toList();
    }

    @Override public List<ReportRow> paymentRows(ReportingPeriod p, String breakdown) {
        String label = switch (breakdown) {
            case "RECEPTIONIST" -> "CASE WHEN pa.channel='ONLINE' THEN NULL ELSE COALESCE(sp.display_name,acc.display_name,acc.full_name,'Không rõ') END";
            case "METHOD" -> METHOD;
            default -> "to_char(pa.confirmed_at AT TIME ZONE '" + VN + "','YYYY-MM-DD')";
        };
        String sql = "SELECT " + label + " label,count(*) n,COALESCE(sum(pa.amount),0) total FROM payment_attempts pa "
                + "LEFT JOIN accounts acc ON acc.id=pa.created_by_account_id LEFT JOIN staff_profiles sp ON sp.account_id=pa.created_by_account_id "
                + "WHERE pa.status='CONFIRMED' AND pa.confirmed_at>=:from AND pa.confirmed_at<:until GROUP BY 1 ORDER BY 1";
        return periodRows(sql, p).stream().map(row -> new ReportRow((String) row[0], number(row[1]).longValue(),
                number(row[2]).longValue(), null)).toList();
    }

    @Override public List<ReportRow> orderRows(ReportingPeriod p) {
        List<ReportRow> rows = new ArrayList<>();
        for (String code : List.of("PENDING_PAYMENT", "CANCELLED")) {
            String where = code.equals("CANCELLED") ? "(o.order_status='CANCELLED' OR e.execution_status='CANCELLED')"
                    : "(o.order_status<>'CANCELLED' AND e.execution_status IS DISTINCT FROM 'CANCELLED' AND o.payment_status='PENDING_PAYMENT')";
            Object[] row = (Object[]) single("SELECT count(*) n,COALESCE(sum(o.price_snapshot),0) total FROM " + ORDER_FROM
                    + " WHERE " + where + " AND r.ordered_at>=:from AND r.ordered_at<:until", p);
            rows.add(new ReportRow(code, number(row[0]).longValue(), number(row[1]).longValue(), null));
        }
        Object[] refund = (Object[]) single("SELECT count(*) n,COALESCE(sum(i.unit_price_snapshot*i.quantity),0) total FROM invoice_items i "
                + "JOIN invoices v ON v.id=i.invoice_id WHERE i.refund_status='REFUND_PENDING' AND v.issued_at>=:from AND v.issued_at<:until", p);
        rows.add(new ReportRow("REFUND_PENDING", number(refund[0]).longValue(), number(refund[1]).longValue(), null));
        return rows;
    }

    @Override public List<ReportRow> reviewRows(ReportingPeriod p) {
        return periodRows("SELECT to_char(submitted_at AT TIME ZONE '" + VN + "','YYYY-MM-DD') label,count(*) n,avg(rating) mean "
                + "FROM patient_reviews WHERE status='PUBLISHED' AND submitted_at>=:from AND submitted_at<:until GROUP BY 1 ORDER BY 1", p)
                .stream().map(row -> new ReportRow((String) row[0], number(row[1]).longValue(), null, round(number(row[2]).doubleValue()))).toList();
    }

    private Map<String, Long> counts(String sql, ReportingPeriod p) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Object[] row : periodRows(sql, p)) result.put(row[0].toString(), number(row[1]).longValue());
        return result;
    }
    private List<Object[]> periodRows(String sql, ReportingPeriod p) {
        Query query = entityManager.createNativeQuery(sql).setParameter("from", Timestamp.from(p.start()))
                .setParameter("until", Timestamp.from(p.until()));
        return queryRows(query);
    }
    private List<Object[]> queryRows(Query query) {
        List<?> results = query.getResultList();
        List<Object[]> rows = new ArrayList<>(results.size());
        for (Object result : results) rows.add(array(result));
        return rows;
    }
    private Object single(String sql, ReportingPeriod p) {
        var query = entityManager.createNativeQuery(sql);
        if (p != null) query.setParameter("from", Timestamp.from(p.start())).setParameter("until", Timestamp.from(p.until()));
        return query.getSingleResult();
    }
    private static Object[] array(Object value) { return value instanceof Object[] row ? row : new Object[] { value }; }
    private static Number number(Object value) { return value instanceof Number number ? number : new BigDecimal(value.toString()); }
    static double round(double value) { return Math.round(value * 10.0) / 10.0; }
}
