package fu.fa26.sep490.g58.otcms_be.visit.service;

import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import java.time.Instant;
import java.util.UUID;

public record ExitWalkInReceptionQueueCommand(UUID visitId, UUID actorAccountId, AccountRole actorRole,
        String reason, Instant occurredAt, String correlationId) { }
