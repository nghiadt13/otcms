package fu.fa26.sep490.g58.otcms_be.common.repository;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

/** PostgreSQL transaction lock used to coordinate booking-affecting writes. */
@Repository
public class CoordinationGuardRepository {
    // One shared lock for every transaction that can change booking availability.
    private static final long COORDINATION_LOCK_KEY = 4_903_490_001L;
    private final EntityManager entityManager;

    public CoordinationGuardRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public void setTransactionTimeouts() {
        entityManager.createNativeQuery("SELECT set_config('lock_timeout', '2s', true)").getSingleResult();
        entityManager.createNativeQuery("SELECT set_config('statement_timeout', '5s', true)").getSingleResult();
    }

    public String transactionIsolation() {
        return (String) entityManager.createNativeQuery("SELECT current_setting('transaction_isolation')").getSingleResult();
    }

    public boolean lockGuardRow() {
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(:key)")
                .setParameter("key", COORDINATION_LOCK_KEY).getSingleResult();
        return true;
    }
}
