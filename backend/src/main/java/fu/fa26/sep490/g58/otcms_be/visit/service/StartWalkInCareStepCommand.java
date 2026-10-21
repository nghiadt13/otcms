package fu.fa26.sep490.g58.otcms_be.visit.service;

import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import java.util.UUID;

public class StartWalkInCareStepCommand {
    private final UUID visitId;
    private final UUID actorAccountId;
    private final AccountRole actorRole;
    private final String stepCode;
    private final String correlationId;

    public StartWalkInCareStepCommand(UUID visitId, UUID actorAccountId, AccountRole actorRole, String stepCode,
            String correlationId) {
        this.visitId = visitId;
        this.actorAccountId = actorAccountId;
        this.actorRole = actorRole;
        this.stepCode = stepCode;
        this.correlationId = correlationId;
    }

    public UUID visitId() { return visitId; }
    public UUID actorAccountId() { return actorAccountId; }
    public AccountRole actorRole() { return actorRole; }
    public String stepCode() { return stepCode; }
    public String correlationId() { return correlationId; }
}
