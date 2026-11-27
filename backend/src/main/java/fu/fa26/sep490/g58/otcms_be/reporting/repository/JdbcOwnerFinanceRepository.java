package fu.fa26.sep490.g58.otcms_be.reporting.repository;

import fu.fa26.sep490.g58.otcms_be.reporting.model.ReportingPeriod;
import fu.fa26.sep490.g58.otcms_be.reporting.model.dto.OwnerFinanceDtos.RefundByPerson;
import fu.fa26.sep490.g58.otcms_be.reporting.model.dto.OwnerFinanceDtos.RefundRow;
import fu.fa26.sep490.g58.otcms_be.reporting.model.dto.OwnerFinanceDtos.Summary;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcOwnerFinanceRepository implements OwnerFinanceRepository {
    private static final String LINE = "i.unit_price_snapshot*i.quantity";
    private static final String REFUNDED_IN_PERIOD = "i.refund_status='REFUNDED' AND i.refunded_at>=:from AND i.refunded_at<:until";
    /** Who confirmed a refund: the staff display name, falling back to the account's own names, then its username. */
    private static final String WHO = "COALESCE(sp.display_name,acc.display_name,acc.full_name,acc.username)";
    private static final String CONFIRMER_JOINS = "LEFT JOIN accounts acc ON acc.id=i.refunded_by_account_id "
        + "LEFT JOIN staff_profiles sp ON sp.account_id=i.refunded_by_account_id ";

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcOwnerFinanceRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static MapSqlParameterSource range(ReportingPeriod p) {
        return new MapSqlParameterSource("from", Timestamp.from(p.start())).addValue("until", Timestamp.from(p.until()));
    }

    @Override
    public Summary summary(ReportingPeriod period) {
        long collected = jdbc.queryForObject("SELECT COALESCE(SUM(pa.amount),0) FROM payment_attempts pa "
            + "WHERE pa.status='CONFIRMED' AND pa.confirmed_at>=:from AND pa.confirmed_at<:until", range(period), Long.class);
        return jdbc.queryForObject("SELECT COALESCE(SUM(" + LINE + ") FILTER (WHERE " + REFUNDED_IN_PERIOD + "),0) AS refunded, "
            + "count(*) FILTER (WHERE " + REFUNDED_IN_PERIOD + ") AS refunded_count, "
            + "COALESCE(SUM(" + LINE + ") FILTER (WHERE i.refund_status='REFUND_PENDING'),0) AS pending, "
            + "count(*) FILTER (WHERE i.refund_status='REFUND_PENDING') AS pending_count FROM invoice_items i "
            + "WHERE i.refund_status IS NOT NULL", range(period),
            (rs, n) -> new Summary(collected, rs.getBigDecimal("refunded").longValue(), rs.getLong("refunded_count"),
                rs.getBigDecimal("pending").longValue(), rs.getLong("pending_count")));
    }

    @Override
    public List<RefundByPerson> refundsByPerson(ReportingPeriod period) {
        return jdbc.query("SELECT " + WHO + " AS who, acc.account_type AS role, count(*) AS n, SUM(" + LINE + ") AS amount "
            + "FROM invoice_items i " + CONFIRMER_JOINS + "WHERE " + REFUNDED_IN_PERIOD
            + " GROUP BY " + WHO + ", acc.account_type ORDER BY amount DESC, who", range(period),
            (rs, n) -> new RefundByPerson(rs.getString("who"), rs.getString("role"), rs.getLong("n"), rs.getBigDecimal("amount").longValue()));
    }

    @Override
    public List<RefundRow> refunds(ReportingPeriod period, int limit) {
        return jdbc.query("SELECT i.id, v.invoice_number, pp.full_name AS patient, i.description_snapshot, " + LINE + " AS amount, "
            + "i.refund_status, i.refund_reason, pa.confirmed_at AS paid_at, i.refunded_at, " + WHO + " AS who, acc.account_type AS role "
            + "FROM invoice_items i JOIN invoices v ON v.id=i.invoice_id "
            + "LEFT JOIN patient_profiles pp ON pp.id=v.patient_profile_id "
            + "LEFT JOIN payment_attempts pa ON pa.id=i.payment_attempt_id " + CONFIRMER_JOINS
            + "WHERE i.refund_status='REFUND_PENDING' OR (" + REFUNDED_IN_PERIOD + ") "
            + "ORDER BY (i.refund_status='REFUND_PENDING') DESC, "
            + "CASE WHEN i.refund_status='REFUND_PENDING' THEN pa.confirmed_at END ASC, i.refunded_at DESC, i.id LIMIT :limit",
            range(period).addValue("limit", limit),
            (rs, n) -> new RefundRow(rs.getString("id"), rs.getString("invoice_number"), rs.getString("patient"),
                rs.getString("description_snapshot"), rs.getBigDecimal("amount").longValue(), rs.getString("refund_status"),
                rs.getString("refund_reason"), instant(rs.getTimestamp("paid_at")), instant(rs.getTimestamp("refunded_at")),
                rs.getString("who"), rs.getString("role")));
    }

    private static java.time.Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
