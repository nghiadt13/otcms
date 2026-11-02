package fu.fa26.sep490.g58.otcms_be.visit.service;

import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import java.util.UUID;

public class ExtendVisitCareAssignmentCommand {
    private final UUID visitId;
    private final UUID assignmentId;
    private final UUID actorAccountId;
    private final AccountRole actorRole;
    private final String stepCode;
    private final String correlationId;

    public ExtendVisitCareAssignmentCommand(UUID visitId, UUID assignmentId, UUID actorAccountId,
            AccountRole actorRole, String stepCode, String correlationId) {
        this.visitId = visitId;
        this.assignmentId = assignmentId;
        this.actorAccountId = actorAccountId;
        this.actorRole = actorRole;
        this.stepCode = stepCode;
        this.correlationId = correlationId;
    }

    public UUID visitId() { return visitId; }
    public UUID assignmentId() { return assignmentId; }
    public UUID actorAccountId() { return actorAccountId; }
    public AccountRole actorRole() { return actorRole; }
    public String stepCode() { return stepCode; }
    public String correlationId() { return correlationId; }
}
