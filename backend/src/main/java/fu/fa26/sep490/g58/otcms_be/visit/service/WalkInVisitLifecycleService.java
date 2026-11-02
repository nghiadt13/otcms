package fu.fa26.sep490.g58.otcms_be.visit.service;

import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import java.util.UUID;

public interface WalkInVisitLifecycleService {
    void cancel(UUID visitId, UUID actorAccountId, AccountRole actorRole, String correlationId);
    void close(UUID visitId, UUID actorAccountId, AccountRole actorRole, String correlationId);
}
