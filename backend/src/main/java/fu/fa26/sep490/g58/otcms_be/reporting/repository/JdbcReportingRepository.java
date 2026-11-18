package fu.fa26.sep490.g58.otcms_be.reporting.repository;

import fu.fa26.sep490.g58.otcms_be.reporting.model.DailyCount;
import fu.fa26.sep490.g58.otcms_be.reporting.model.ReportingPeriod;
import fu.fa26.sep490.g58.otcms_be.reporting.model.ServiceCount;
import fu.fa26.sep490.g58.otcms_be.reporting.repository.ReportingRepository;
import fu.fa26.sep490.g58.otcms_be.reporting.model.DashboardSections.ReviewStats;
import fu.fa26.sep490.g58.otcms_be.reporting.model.DashboardSections.RoomWaiting;
import fu.fa26.sep490.g58.otcms_be.reporting.model.ReportRow;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcReportingRepository implements ReportingRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public JdbcReportingRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc=jdbc;
    }
    private MapSqlParameterSource params(ReportingPeriod p) {
        return new MapSqlParameterSource("from",Timestamp.from(p.start())).addValue("until",Timestamp.from(p.until()));
    }
    private Map<String,Long> counts(String sql,ReportingPeriod p) {
        var result=new LinkedHashMap<String,Long>();
        jdbc.query(sql,params(p),rs->{
                result.put(rs.getString("code"),rs.getLong("n"));
        });
        return result;
    }
    @Override
    public Map<String,Long> appointmentCounts(ReportingPeriod p) {
        return counts("""
            SELECT CASE WHEN status='CONFIRMED' THEN 'BOOKED' WHEN status IN ('IN_PROGRESS','COMPLETED') THEN 'CHECKED_IN' ELSE status END code,count(*) n
            FROM appointments WHERE start_time>=:from AND start_time<:until
              AND status NOT IN ('AWAITING_PAYMENT','PAYMENT_EXPIRED') GROUP BY 1
            """,p);
    }
    @Override
    public Map<String,Long> visitCounts(ReportingPeriod p) {
        return counts("""
            SELECT CASE WHEN v.status='CLOSED' THEN 'COMPLETED' WHEN v.status='CANCELLED' THEN 'CANCELLED'
              WHEN EXISTS(SELECT 1 FROM visit_assignments a WHERE a.visit_id=v.id AND a.status='IN_PROGRESS' AND a.actual_ended_at IS NULL) THEN 'IN_PROGRESS' ELSE 'WAITING' END code,count(*) n
            FROM visits v WHERE v.checked_in_at>=:from AND v.checked_in_at<:until GROUP BY 1
            """,p);
    }
    @Override
    public List<DailyCount> daily(String subject,ReportingPeriod p) {
        // Only these fixed identifiers are allowed; no request string is interpolated into SQL.
        String table=switch(subject){case "APPOINTMENTS"->"appointments";case "IMAGING"->"clinical_service_orders";default->"visits";};
        String column=switch(subject){case "APPOINTMENTS"->"start_time";case "IMAGING"->IMAGING_TIME;default->"checked_in_at";};
        // Payment-hold states are technical: they are never an appointment in any report.
        String filter=switch(subject){case "APPOINTMENTS"->" AND status NOT IN ('AWAITING_PAYMENT','PAYMENT_EXPIRED')";case "IMAGING"->" AND "+IMAGING_DONE;default->"";};
        var rows=jdbc.query("SELECT to_char("+column+" AT TIME ZONE 'Asia/Ho_Chi_Minh','YYYY-MM-DD') AS report_date,count(*) n FROM "+table+" WHERE "+column+">=:from AND "+column+"<:until"+filter+" GROUP BY 1 ORDER BY 1",params(p),
            (rs,i)->new DailyCount(rs.getString("report_date"), rs.getLong("n")));
        return rows;
    }
    @Override
    public List<ServiceCount> services(ReportingPeriod p) {
        return jdbc.query("""
            SELECT a.service_id,count(*) n FROM appointments a
            WHERE a.status='COMPLETED' AND a.start_time>=:from AND a.start_time<:until
            GROUP BY a.service_id ORDER BY n DESC,a.service_id
            """,params(p),(rs,i)->new ServiceCount(rs.getString("service_id"), rs.getLong("n")));
    }
    private static final String VN = "Asia/Ho_Chi_Minh";
    /** Imaging counts as performed once its result exists; the capture time is the first use of the attempt. */
    private static final String IMAGING_TIME = "COALESCE(first_use_at, execution_updated_at)";
    private static final String IMAGING_DONE = "service_type='IMAGING' AND execution_status IN ('RESULT_AVAILABLE','REVIEWED')";

    @Override
    public long imagingPerformed(ReportingPeriod p) {
        return jdbc.queryForObject("SELECT count(*) FROM clinical_service_orders WHERE " + IMAGING_DONE
            + " AND " + IMAGING_TIME + ">=:from AND " + IMAGING_TIME + "<:until", params(p), Long.class);
    }
    /** Counter payments are cash; ONLINE and COUNTER_QR both go through PayOS. */
    private static final String METHOD = "CASE WHEN pa.channel='COUNTER' THEN 'CASH' ELSE 'PAYOS' END";
    @Override
    public Map<String,Long> paymentsByMethod(ReportingPeriod p) {
        var result=new LinkedHashMap<String,Long>();
        jdbc.query("SELECT "+METHOD+" code, COALESCE(sum(pa.amount),0) n FROM payment_attempts pa WHERE pa.status='CONFIRMED' "
            + "AND pa.confirmed_at>=:from AND pa.confirmed_at<:until GROUP BY 1",params(p),
            rs->{ result.put(rs.getString("code"),rs.getBigDecimal("n").longValue()); });
        return result;
    }
    @Override
    public Map<String,Long> orderCounts(ReportingPeriod p) {
        return counts("""
            SELECT CASE WHEN order_status='CANCELLED' OR execution_status='CANCELLED' THEN 'CANCELLED' ELSE 'PENDING_PAYMENT' END code,count(*) n
            FROM clinical_service_orders WHERE ordered_at>=:from AND ordered_at<:until
              AND (order_status='CANCELLED' OR execution_status='CANCELLED' OR payment_status='PENDING_PAYMENT') GROUP BY 1
            """,p);
    }
    @Override
    public long refundPendingItems() {
        return jdbc.queryForObject("SELECT count(*) FROM invoice_items WHERE refund_status='REFUND_PENDING'",new MapSqlParameterSource(),Long.class);
    }
    @Override
    public ReviewStats reviewStats(ReportingPeriod p) {
        return jdbc.queryForObject("SELECT count(*) n, avg(rating) mean FROM patient_reviews WHERE status='PUBLISHED' "
            + "AND submitted_at>=:from AND submitted_at<:until",params(p),(rs,i)->{
                double mean=rs.getDouble("mean");
                return new ReviewStats(rs.getLong("n"),rs.wasNull()?null:mean);
            });
    }
    @Override
    public long pendingReviews() {
        return jdbc.queryForObject("SELECT count(*) FROM patient_reviews WHERE status='PENDING'",new MapSqlParameterSource(),Long.class);
    }
    @Override
    public List<RoomWaiting> waitingByRoom(LocalDate today) {
        return jdbc.query("""
            SELECT r.id, r.display_label, count(*) n FROM queue_entries q JOIN rooms r ON r.id=q.room_id
            WHERE q.stage IN ('EXAM','SERVICE') AND q.status IN ('WAITING','CALLED')
              AND (q.stage='SERVICE' OR q.clinic_date=:today)
            GROUP BY r.id,r.display_label ORDER BY n DESC,r.display_label,r.id
            """,new MapSqlParameterSource("today",java.sql.Date.valueOf(today)),
            (rs,i)->new RoomWaiting(rs.getString("id"),rs.getString("display_label"),rs.getLong("n")));
    }
    @Override
    public List<ReportRow> paymentRows(ReportingPeriod p,String breakdown) {
        // Only these fixed expressions are used; no request string is interpolated into SQL.
        String label=switch(breakdown){
            case "RECEPTIONIST"->"CASE WHEN pa.channel='ONLINE' THEN NULL ELSE COALESCE(sp.display_name,acc.display_name,acc.full_name,'Không rõ') END";
            case "METHOD"->METHOD;
            default->"to_char(pa.confirmed_at AT TIME ZONE '"+VN+"','YYYY-MM-DD')";
        };
        return jdbc.query("SELECT "+label+" AS label,count(*) n,COALESCE(sum(pa.amount),0) total FROM payment_attempts pa "
            + "LEFT JOIN accounts acc ON acc.id=pa.created_by_account_id LEFT JOIN staff_profiles sp ON sp.account_id=pa.created_by_account_id "
            + "WHERE pa.status='CONFIRMED' AND pa.confirmed_at>=:from AND pa.confirmed_at<:until GROUP BY 1 ORDER BY 1",params(p),
            (rs,i)->new ReportRow(rs.getString("label"),rs.getLong("n"),rs.getBigDecimal("total").longValue(),null));
    }
    @Override
    public List<ReportRow> orderRows(ReportingPeriod p) {
        var rows=new ArrayList<ReportRow>();
        for(String code:List.of("PENDING_PAYMENT","CANCELLED")){
            String where=code.equals("CANCELLED")?"(order_status='CANCELLED' OR execution_status='CANCELLED')"
                :"(order_status<>'CANCELLED' AND execution_status IS DISTINCT FROM 'CANCELLED' AND payment_status='PENDING_PAYMENT')";
            rows.add(jdbc.queryForObject("SELECT count(*) n,COALESCE(sum(price_snapshot),0) total FROM clinical_service_orders WHERE "+where
                +" AND ordered_at>=:from AND ordered_at<:until",params(p),
                (rs,i)->new ReportRow(code,rs.getLong("n"),rs.getBigDecimal("total").longValue(),null)));
        }
        // Items waiting for a manual refund, anchored on the invoice issue date so the period still means something.
        rows.add(jdbc.queryForObject("SELECT count(*) n,COALESCE(sum(i.unit_price_snapshot*i.quantity),0) total FROM invoice_items i "
            +"JOIN invoices v ON v.id=i.invoice_id WHERE i.refund_status='REFUND_PENDING' AND v.issued_at>=:from AND v.issued_at<:until",params(p),
            (rs,i)->new ReportRow("REFUND_PENDING",rs.getLong("n"),rs.getBigDecimal("total").longValue(),null)));
        return rows;
    }
    @Override
    public List<ReportRow> reviewRows(ReportingPeriod p) {
        return jdbc.query("SELECT to_char(submitted_at AT TIME ZONE '"+VN+"','YYYY-MM-DD') AS label,count(*) n,avg(rating) mean FROM patient_reviews "
            +"WHERE status='PUBLISHED' AND submitted_at>=:from AND submitted_at<:until GROUP BY 1 ORDER BY 1",params(p),
            (rs,i)->new ReportRow(rs.getString("label"),rs.getLong("n"),null,round(rs.getDouble("mean"))));
    }
    /** One decimal is enough for a 1-5 rating and keeps screen and CSV identical. */
    static double round(double value) {
        return Math.round(value*10.0)/10.0;
    }
}
