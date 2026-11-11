package fu.fa26.sep490.g58.otcms_be.visit.service;

import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.visit.model.VisitCompletionResult;
import java.util.UUID;

public interface WalkInVisitLifecycleService {
    void cancel(UUID visitId, UUID actorAccountId, AccountRole actorRole, String correlationId);
    void close(UUID visitId, UUID actorAccountId, AccountRole actorRole, String correlationId);

    /** Responsible DOCTOR closes a WALK_IN visit they are examining (POST /api/v1/visits/{visitId}/completion). */
    VisitCompletionResult completeWalkIn(UUID visitId, UUID actorAccountId, AccountRole actorRole, String correlationId);

    /**
     * Responsible doctor closes any visit whose queue entry is theirs. Shared by the walk-in endpoint and the
     * scheduled-appointment completion; the caller owns the transaction.
     */
    VisitCompletionResult completeByDoctor(UUID visitId, UUID doctorProfileId, UUID actorAccountId, String correlationId);
}
