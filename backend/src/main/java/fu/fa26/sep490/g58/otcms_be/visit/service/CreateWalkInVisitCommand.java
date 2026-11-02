package fu.fa26.sep490.g58.otcms_be.visit.service;

import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import java.util.UUID;

public class CreateWalkInVisitCommand {
    private final UUID actorAccountId;
    private final AccountRole actorRole;
    private final UUID patientProfileId;
    private final UUID priorAppointmentId;
    private final String idempotencyKey;
    private final String correlationId;

    public CreateWalkInVisitCommand(UUID actorAccountId, AccountRole actorRole, UUID patientProfileId,
            UUID priorAppointmentId, String idempotencyKey, String correlationId) {
        this.actorAccountId = actorAccountId;
        this.actorRole = actorRole;
        this.patientProfileId = patientProfileId;
        this.priorAppointmentId = priorAppointmentId;
        this.idempotencyKey = idempotencyKey;
        this.correlationId = correlationId;
    }

    public UUID actorAccountId() { return actorAccountId; }
    public AccountRole actorRole() { return actorRole; }
    public UUID patientProfileId() { return patientProfileId; }
    public UUID priorAppointmentId() { return priorAppointmentId; }
    public String idempotencyKey() { return idempotencyKey; }
    public String correlationId() { return correlationId; }
}
