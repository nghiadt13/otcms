package fu.fa26.sep490.g58.otcms_be.visit.service;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import java.util.UUID;

public record CreateWalkInReceptionQueueEntryCommand(UUID visitId, AuthenticatedAccount actor,
        String correlationId) { }
