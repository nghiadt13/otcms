package fu.fa26.sep490.g58.otcms_be.visit.repository;

import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class WalkInVisitLifecycleRepository {
    private final EntityManager entityManager;

    public WalkInVisitLifecycleRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public boolean hasCareStarted(UUID visitId) {
        Number count = (Number) entityManager.createNativeQuery("""
                select count(*) from visit_assignments
                where visit_id = :visitId and status in ('IN_PROGRESS', 'COMPLETED')
                """).setParameter("visitId", visitId).getSingleResult();
        return count.longValue() > 0;
    }

    public boolean hasInProgressCare(UUID visitId) {
        Number count = (Number) entityManager.createNativeQuery("""
                select count(*) from visit_assignments where visit_id = :visitId and status = 'IN_PROGRESS'
                """).setParameter("visitId", visitId).getSingleResult();
        return count.longValue() > 0;
    }

    public boolean closeOpenWalkIn(UUID visitId, java.time.Instant now) {
        Number changed = (Number) entityManager.createNativeQuery("""
                update visits set status = 'CLOSED', updated_at = :now
                where id = :visitId and source = 'WALK_IN' and status = 'OPEN'
                """).setParameter("visitId", visitId).setParameter("now", Timestamp.from(now)).executeUpdate();
        return changed.intValue() == 1;
    }

    public void releaseReservedCare(UUID visitId, java.time.Instant now) {
        entityManager.createNativeQuery("""
                update visit_assignments set status = 'RELEASED', released_at = :now
                where visit_id = :visitId and status = 'RESERVED'
                """).setParameter("visitId", visitId).setParameter("now", Timestamp.from(now)).executeUpdate();
    }
}
