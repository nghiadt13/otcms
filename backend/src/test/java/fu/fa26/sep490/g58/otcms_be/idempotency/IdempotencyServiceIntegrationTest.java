package fu.fa26.sep490.g58.otcms_be.idempotency;

import static org.junit.jupiter.api.Assertions.*;

import fu.fa26.sep490.g58.otcms_be.idempotency.model.IdempotencyScope;
import fu.fa26.sep490.g58.otcms_be.idempotency.model.dto.IdempotencyReplay;
import fu.fa26.sep490.g58.otcms_be.idempotency.service.IdempotencyService;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** V139 idempotency_records: store, find, mismatch detection and two concurrent writers on one key (real PostgreSQL). */
@SpringBootTest
@LocalPostgresTest
class IdempotencyServiceIntegrationTest {
    private static final String FP_A = "a".repeat(64);
    private static final String FP_B = "b".repeat(64);

    @Autowired IdempotencyService store;
    @Autowired JdbcTemplate jdbc;

    @Test
    void recordsThenFindsTheSameKeyAndFingerprint() {
        UUID owner = UUID.randomUUID();
        UUID resource = UUID.randomUUID();
        String key = "key-" + UUID.randomUUID();

        assertTrue(store.find(IdempotencyScope.PAYMENT_ATTEMPT, owner, key).isEmpty());
        assertTrue(store.record(IdempotencyScope.PAYMENT_ATTEMPT, owner, key, FP_A, resource));

        IdempotencyReplay replay = store.find(IdempotencyScope.PAYMENT_ATTEMPT, owner, key).orElseThrow();
        assertEquals(resource, replay.resourceId());
        assertTrue(replay.matches(FP_A));
        assertTrue(replay.matches(FP_A.toUpperCase()));
        assertFalse(replay.matches(FP_B));
        cleanup(owner);
    }

    @Test
    void sameKeyIsScopedByScopeAndOwner() {
        UUID owner = UUID.randomUUID();
        UUID otherOwner = UUID.randomUUID();
        String key = "key-" + UUID.randomUUID();

        assertTrue(store.record(IdempotencyScope.RESULT_DRAFT, owner, key, FP_A, UUID.randomUUID()));
        assertTrue(store.record(IdempotencyScope.RESULT_COMPLETE, owner, key, FP_A, UUID.randomUUID()));
        assertTrue(store.record(IdempotencyScope.RESULT_DRAFT, otherOwner, key, FP_A, UUID.randomUUID()));
        assertFalse(store.record(IdempotencyScope.RESULT_DRAFT, owner, key, FP_B, UUID.randomUUID()),
                "same scope + owner + key must not be stored twice");
        // the first writer keeps the key: the loser's fingerprint is not stored
        assertTrue(store.find(IdempotencyScope.RESULT_DRAFT, owner, key).orElseThrow().matches(FP_A));
        cleanup(owner);
        cleanup(otherOwner);
    }

    @Test
    void twoConcurrentWritersOnOneKeyProduceExactlyOneWinner() throws Exception {
        UUID owner = UUID.randomUUID();
        String key = "key-" + UUID.randomUUID();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return store.record(IdempotencyScope.APPOINTMENT_CHECKIN, owner, key, FP_A, UUID.randomUUID());
                }));
            }
            start.countDown();
            int winners = 0;
            for (Future<Boolean> f : results) {
                if (f.get(30, TimeUnit.SECONDS)) winners++;
            }
            assertEquals(1, winners);
            assertEquals(1, jdbc.queryForObject(
                    "select count(*) from idempotency_records where owner_id = ?", Integer.class, owner));
        } finally {
            pool.shutdownNow();
            cleanup(owner);
        }
    }

    private void cleanup(UUID owner) {
        jdbc.update("delete from idempotency_records where owner_id = ?", owner);
    }
}
