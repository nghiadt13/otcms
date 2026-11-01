package fu.fa26.sep490.g58.otcms_be.reporting.adapter.out.persistence;
import fu.fa26.sep490.g58.otcms_be.reporting.application.ReportingReader;
import fu.fa26.sep490.g58.otcms_be.reporting.domain.ReportingPeriod;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
@Repository
public class JdbcReportingReader implements ReportingReader {
    private final NamedParameterJdbcTemplate jdbc;
    public JdbcReportingReader(NamedParameterJdbcTemplate jdbc) {this.jdbc=jdbc;}
    private MapSqlParameterSource params(ReportingPeriod p) {return new MapSqlParameterSource("from",Timestamp.from(p.start())).addValue("until",Timestamp.from(p.until()));}
    private Map<String,Long> counts(String sql,ReportingPeriod p) {
        var result=new LinkedHashMap<String,Long>();
        jdbc.query(sql,params(p),rs->{result.put(rs.getString("code"),rs.getLong("n"));});return result;
    }
    @Override public Map<String,Long> appointmentCounts(ReportingPeriod p) {
        return counts("""
            SELECT CASE WHEN status='CONFIRMED' THEN 'BOOKED' WHEN status IN ('IN_PROGRESS','COMPLETED') THEN 'CHECKED_IN' ELSE status END code,count(*) n
            FROM appointments WHERE start_time>=:from AND start_time<:until GROUP BY 1
            """,p);
    }
    @Override public Map<String,Long> visitCounts(ReportingPeriod p) {
        return counts("""
            SELECT CASE WHEN v.status='CLOSED' THEN 'COMPLETED' WHEN v.status='CANCELLED' THEN 'CANCELLED'
              WHEN EXISTS(SELECT 1 FROM visit_assignments a WHERE a.visit_id=v.id AND a.status='IN_PROGRESS' AND a.actual_ended_at IS NULL) THEN 'IN_PROGRESS' ELSE 'WAITING' END code,count(*) n
            FROM visits v WHERE v.checked_in_at>=:from AND v.checked_in_at<:until GROUP BY 1
            """,p);
    }
    @Override public List<Map<String,Object>> daily(String subject,ReportingPeriod p) {
        // Only these fixed identifiers are allowed; no request string is interpolated into SQL.
        String table=subject.equals("APPOINTMENTS")?"appointments":"visits";
        String column=subject.equals("APPOINTMENTS")?"start_time":"checked_in_at";
        var rows=jdbc.query("SELECT to_char("+column+" AT TIME ZONE 'Asia/Ho_Chi_Minh','YYYY-MM-DD') AS report_date,count(*) n FROM "+table+" WHERE "+column+">=:from AND "+column+"<:until GROUP BY 1 ORDER BY 1",params(p),
                (rs,i)->Map.<String,Object>of("date",rs.getString("report_date"),"count",rs.getLong("n")));
        return rows;
    }
    @Override public List<Map<String,Object>> services(ReportingPeriod p) {
        return jdbc.query("""
            SELECT c.id,c.name,c.service_type,count(*) n FROM appointments a JOIN clinic_services c ON c.id=a.service_id
            WHERE a.status='COMPLETED' AND a.start_time>=:from AND a.start_time<:until
            GROUP BY c.id,c.name,c.service_type ORDER BY n DESC,c.id
            """,params(p),(rs,i)->Map.<String,Object>of("serviceId",rs.getString("id"),"name",rs.getString("name"),"category",rs.getString("service_type"),"value",rs.getLong("n")));
    }
}

