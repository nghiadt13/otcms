package fu.fa26.sep490.g58.otcms_be.reporting.repository;

import fu.fa26.sep490.g58.otcms_be.reporting.model.ReportingPeriod;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * SQL of the Owner dashboard. Revenue is what was actually collected: a payment attempt counts once it is CONFIRMED,
 * on the day it was confirmed, and an item whose refund was completed ({@code REFUNDED}) no longer counts. Payment is
 * per item (an attempt pays exactly the items that point at it), so the per-doctor and per-service figures are sums of
 * the same item lines and add up to the daily revenue.
 */
@Repository
public class JdbcOwnerAnalyticsRepository implements OwnerAnalyticsRepository {
    private static final String VN = "Asia/Ho_Chi_Minh";
    /** Counter payments are cash; ONLINE and COUNTER_QR both go through PayOS. */
    private static final String METHOD = "CASE WHEN pa.channel='COUNTER' THEN 'CASH' ELSE 'PAYOS' END";
    private static final String KEPT_ITEM = "i.refund_status IS DISTINCT FROM 'REFUNDED'";
    /**
     * Doctor that earned an invoice: the appointment's doctor for a booking fee, the doctor of the visit's
     * examination queue entry for a visit fee, the ordering doctor for a service order round.
     */
    private static final String INVOICE_DOCTOR = """
        CASE inv.source_type
          WHEN 'APPOINTMENT' THEN (SELECT a.doctor_profile_id FROM appointments a WHERE a.id=inv.appointment_id)
          WHEN 'VISIT' THEN %s
          WHEN 'CLINICAL_ROUND' THEN (SELECT r.ordered_by_doctor_id FROM service_order_rounds r WHERE r.id=inv.clinical_ordering_round_id)
        END""".formatted(visitDoctor("inv.visit_id"));

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcOwnerAnalyticsRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** The doctor a visit was queued to for the examination (the first one if it was handed over). */
    private static String visitDoctor(String visitId) {
        return "(SELECT q.doctor_profile_id FROM queue_entries q WHERE q.visit_id=" + visitId
            + " AND q.stage='EXAM' AND q.doctor_profile_id IS NOT NULL ORDER BY q.entered_at, q.id LIMIT 1)";
    }

    private static MapSqlParameterSource range(Instant from, Instant until) {
        return new MapSqlParameterSource("from", Timestamp.from(from)).addValue("until", Timestamp.from(until));
    }

    private static MapSqlParameterSource range(ReportingPeriod p) {
        return range(p.start(), p.until());
    }

    @Override
    public List<DailyAmount> dailyRevenue(Instant from, Instant until) {
        return jdbc.query("SELECT to_char(pa.confirmed_at AT TIME ZONE '" + VN + "','YYYY-MM-DD') AS day, " + METHOD + " AS method, "
            + "SUM(pa.amount) - COALESCE(SUM(refunded.total),0) AS amount FROM payment_attempts pa "
            + "LEFT JOIN LATERAL (SELECT SUM(i.unit_price_snapshot*i.quantity) AS total FROM invoice_items i "
            + "WHERE i.payment_attempt_id=pa.id AND i.refund_status='REFUNDED') refunded ON true "
            + "WHERE pa.status='CONFIRMED' AND pa.confirmed_at>=:from AND pa.confirmed_at<:until GROUP BY 1,2 ORDER BY 1,2",
            range(from, until), (rs, i) -> new DailyAmount(rs.getString("day"), rs.getString("method"), rs.getBigDecimal("amount").longValue()));
    }

    @Override
    public List<DoctorAmount> doctorRevenue(ReportingPeriod period) {
        return jdbc.query("SELECT d.doctor_id, sp.display_name, SUM(i.unit_price_snapshot*i.quantity) AS amount "
            + "FROM payment_attempts pa JOIN invoice_items i ON i.payment_attempt_id=pa.id AND " + KEPT_ITEM + " "
            + "JOIN invoices inv ON inv.id=i.invoice_id CROSS JOIN LATERAL (SELECT " + INVOICE_DOCTOR + " AS doctor_id) d "
            + "LEFT JOIN staff_profiles sp ON sp.id=d.doctor_id "
            + "WHERE pa.status='CONFIRMED' AND pa.confirmed_at>=:from AND pa.confirmed_at<:until "
            + "GROUP BY d.doctor_id, sp.display_name ORDER BY amount DESC, sp.display_name",
            range(period), (rs, i) -> new DoctorAmount(rs.getObject("doctor_id", UUID.class), rs.getString("display_name"),
                rs.getBigDecimal("amount").longValue()));
    }

    @Override
    public List<DoctorActivity> doctorActivity(ReportingPeriod period) {
        return jdbc.query("SELECT d.doctor_id, sp.display_name, count(*) AS visits, "
            + "count(DISTINCT (v.checked_in_at AT TIME ZONE '" + VN + "')::date) AS active_days "
            + "FROM visits v CROSS JOIN LATERAL (SELECT " + visitDoctor("v.id") + " AS doctor_id) d "
            + "JOIN staff_profiles sp ON sp.id=d.doctor_id "
            + "WHERE v.status='CLOSED' AND v.checked_in_at>=:from AND v.checked_in_at<:until GROUP BY d.doctor_id, sp.display_name",
            range(period), (rs, i) -> new DoctorActivity(rs.getObject("doctor_id", UUID.class), rs.getString("display_name"),
                rs.getLong("visits"), rs.getLong("active_days")));
    }

    @Override
    public List<ServiceAmount> serviceRevenue(ReportingPeriod period) {
        return jdbc.query("SELECT cs.id, cs.name, cs.service_type, SUM(i.quantity) AS cnt, SUM(i.unit_price_snapshot*i.quantity) AS amount "
            + "FROM payment_attempts pa JOIN invoice_items i ON i.payment_attempt_id=pa.id AND " + KEPT_ITEM + " "
            + "JOIN clinic_services cs ON cs.id=i.service_id "
            + "WHERE pa.status='CONFIRMED' AND pa.confirmed_at>=:from AND pa.confirmed_at<:until "
            + "GROUP BY cs.id, cs.name, cs.service_type ORDER BY amount DESC, cs.name",
            range(period), (rs, i) -> new ServiceAmount(rs.getObject("id", UUID.class), rs.getString("name"),
                rs.getString("service_type"), rs.getLong("cnt"), rs.getBigDecimal("amount").longValue()));
    }

    @Override
    public AppointmentFunnel appointmentFunnel(ReportingPeriod period) {
        // Payment-hold states are technical: they are never an appointment in any report.
        return jdbc.queryForObject("SELECT count(*) AS booked, "
            + "count(*) FILTER (WHERE status IN ('CHECKED_IN','IN_PROGRESS','COMPLETED')) AS arrived, "
            + "count(*) FILTER (WHERE status='COMPLETED') AS completed, count(*) FILTER (WHERE status='CANCELLED') AS cancelled, "
            + "count(*) FILTER (WHERE status='NO_SHOW') AS no_show FROM appointments "
            + "WHERE start_time>=:from AND start_time<:until AND status NOT IN ('AWAITING_PAYMENT','PAYMENT_EXPIRED')",
            range(period), (rs, i) -> new AppointmentFunnel(rs.getLong("booked"), rs.getLong("arrived"), rs.getLong("completed"),
                rs.getLong("cancelled"), rs.getLong("no_show")));
    }

    @Override
    public long arrivals(ReportingPeriod period) {
        return jdbc.queryForObject("SELECT count(*) FROM visits WHERE checked_in_at>=:from AND checked_in_at<:until", range(period), Long.class);
    }
}
