package fu.fa26.sep490.g58.otcms_be.content.repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Existing account display-name read model; ownership decision is recorded in the PR. */
@Repository
public class ContentAuthorRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public ContentAuthorRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }
    public Map<UUID, String> names(List<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        var result = new HashMap<UUID, String>();
        jdbc.query("SELECT id, coalesce(username, normalized_email) author FROM accounts WHERE id IN (:ids)",
            Map.of("ids", ids), row -> {
                result.put(row.getObject("id", UUID.class), row.getString("author"));
        });
        return result;
    }
}
