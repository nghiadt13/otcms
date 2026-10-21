package fu.fa26.sep490.g58.otcms_be.visit.service;

import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import java.util.UUID;

public class CompleteWalkInCareStepCommand {
    private final UUID visitId;
    private final UUID assignmentId;
    private final UUID actorAccountId;
    private final AccountRole actorRole;
    private final String correlationId;

    public CompleteWalkInCareStepCommand(UUID visitId, UUID assignmentId, UUID actorAccountId,
            AccountRole actorRole, String correlationId) {
        this.visitId = visitId;
        this.assignmentId = assignmentId;
        this.actorAccountId = actorAccountId;
        this.actorRole = actorRole;
        this.correlationId = correlationId;
    }

    public UUID visitId() { return visitId; }
    public UUID assignmentId() { return assignmentId; }
    public UUID actorAccountId() { return actorAccountId; }
    public AccountRole actorRole() { return actorRole; }
    public String correlationId() { return correlationId; }
}
