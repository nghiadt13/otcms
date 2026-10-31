package fu.fa26.sep490.g58.otcms_be.common.repository;

import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.stereotype.Repository;

/** PostgreSQL row lock used to coordinate booking-affecting write transactions. */
@Repository
public class CoordinationGuardRepository {
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
        List<?> rows = entityManager.createNativeQuery(
                "SELECT guard_id FROM transaction_guard WHERE guard_id = 1 FOR UPDATE").getResultList();
        return !rows.isEmpty();
    }
}
