package fu.fa26.sep490.g58.otcms_be.common.idempotency;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Shared idempotency storage (table {@code idempotency_records}, unique on scope + owner + key).
 * <p>
 * Call {@link #record} inside the same transaction as the business write so the key and the result commit together.
 * {@code record} uses {@code ON CONFLICT DO NOTHING} instead of throwing, because a unique violation would abort the
 * surrounding PostgreSQL transaction and the caller could no longer read the winning row to replay it.
 */
@Component
public class IdempotencyStore {
    private final JdbcTemplate jdbc;

    public IdempotencyStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Looks up the record for this scope, owner and key; empty when the key was never used. */
    public Optional<IdempotencyReplay> find(IdempotencyScope scope, UUID ownerId, String key) {
        return jdbc.query("""
                select request_fingerprint, resource_id, created_at from idempotency_records
                where scope = ? and owner_id = ? and idempotency_key = ?
                """, rs -> rs.next()
                        ? Optional.of(new IdempotencyReplay(rs.getString(1), rs.getObject(2, UUID.class),
                                rs.getTimestamp(3).toInstant()))
                        : Optional.<IdempotencyReplay>empty(),
                scope.name(), ownerId, key);
    }

    /**
     * Stores the key for the resource just written.
     *
     * @return true when stored, false when another request already holds the same scope + owner + key
     *         (the caller should then {@link #find} and replay or reject on fingerprint mismatch)
     */
    public boolean record(IdempotencyScope scope, UUID ownerId, String key, String fingerprint, UUID resourceId) {
        return record(scope, ownerId, key, fingerprint, resourceId, Instant.now());
    }

    public boolean record(IdempotencyScope scope, UUID ownerId, String key, String fingerprint, UUID resourceId,
            Instant createdAt) {
        return jdbc.update("""
                insert into idempotency_records (id, scope, owner_id, idempotency_key, request_fingerprint, resource_id, created_at)
                values (?, ?, ?, ?, ?, ?, ?)
                on conflict (scope, owner_id, idempotency_key) do nothing
                """, UUID.randomUUID(), scope.name(), ownerId, key, fingerprint, resourceId,
                Timestamp.from(createdAt)) == 1;
    }
}
