package fu.fa26.sep490.g58.otcms_be.activitylog.adapter.out.persistence;
import fu.fa26.sep490.g58.otcms_be.activitylog.application.ActivityLogReader;
import fu.fa26.sep490.g58.otcms_be.activitylog.domain.ActivityLogEntry;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;
@Repository
public class JdbcActivityLogReader implements ActivityLogReader {
    private final NamedParameterJdbcTemplate jdbc;
    // Explicit safe projection: never select before_state/after_state, payloads or medical content.
    private static final String SELECT = """
        SELECT e.id, e.occurred_at, coalesce(a.username, a.normalized_email, 'system') actor_username,
          CASE a.account_type WHEN 'OWNER' THEN 'CLINIC_OWNER' WHEN 'DOCTOR' THEN 'ORTHOPEDIC_DOCTOR'
            ELSE a.account_type END actor_role,
          e.action, e.category record_type, e.record_label, e.correction_reason, e.record_id::text record_code
        FROM activity_log_entries e LEFT JOIN accounts a ON a.id = e.actor_account_id
        """;
    public JdbcActivityLogReader(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public Page list(Criteria c) {
        var params = new MapSqlParameterSource().addValue("record", c.record()).addValue("actor", c.actor())
                .addValue("category", c.category()).addValue("size", c.size()).addValue("offset", c.page() * c.size());
        var where = new StringBuilder(" WHERE strpos(lower(e.record_label), lower(:record)) > 0"
                + " AND strpos(lower(coalesce(a.username, a.normalized_email, 'system')), lower(:actor)) > 0");
        if (!c.category().isEmpty()) where.append(" AND e.category = :category");
        if (c.from() != null) { where.append(" AND e.occurred_at >= :from"); params.addValue("from", Timestamp.from(c.from())); }
        if (c.until() != null) { where.append(" AND e.occurred_at < :until"); params.addValue("until", Timestamp.from(c.until())); }
        long total = jdbc.queryForObject("SELECT count(*) FROM activity_log_entries e LEFT JOIN accounts a ON a.id=e.actor_account_id" + where,
                params, Long.class);
        List<ActivityLogEntry> items = jdbc.query(SELECT + where + " ORDER BY e.occurred_at DESC, e.id DESC LIMIT :size OFFSET :offset", params,
                JdbcActivityLogReader::map);
        return new Page(items, c.page(), c.size(), total, (int) ((total + c.size() - 1) / c.size()));
    }
    @Override public Optional<ActivityLogEntry> find(String id) {
        return jdbc.query(SELECT + " WHERE e.id = :id", new MapSqlParameterSource("id", id),
                JdbcActivityLogReader::map).stream().findFirst();
    }
    private static ActivityLogEntry map(ResultSet rs, int row) throws SQLException {
        return new ActivityLogEntry(rs.getString("id"), rs.getTimestamp("occurred_at").toInstant(),
                rs.getString("actor_username"), rs.getString("actor_role"), rs.getString("action"), null,
                rs.getString("record_type"), rs.getString("record_label"), rs.getString("correction_reason"),
                rs.getString("record_code"), null);
    }
}
