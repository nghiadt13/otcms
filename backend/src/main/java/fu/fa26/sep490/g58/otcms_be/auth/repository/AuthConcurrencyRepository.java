package fu.fa26.sep490.g58.otcms_be.auth.repository;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

/** Database-level serialization for auth operations that share rate or session limits. */
@Repository
public class AuthConcurrencyRepository {
    private final EntityManager entityManager;

    public AuthConcurrencyRepository(EntityManager entityManager) { this.entityManager = entityManager; }

    public void lock(String key) {
        entityManager.createNativeQuery("select pg_advisory_xact_lock(hashtext(:key))")
                .setParameter("key", key).getSingleResult();
    }
}
