package fu.fa26.sep490.g58.otcms_be.visit.service;

import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import java.util.UUID;

public record CompleteDoctorCareForClosureCommand(
        UUID visitId,
        UUID actorAccountId,
        AccountRole actorRole,
        String correlationId) { }
