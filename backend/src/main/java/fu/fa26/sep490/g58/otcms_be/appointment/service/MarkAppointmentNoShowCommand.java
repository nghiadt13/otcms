package fu.fa26.sep490.g58.otcms_be.appointment.service;

import java.util.UUID;

public class MarkAppointmentNoShowCommand {
    private final UUID actorAccountId;
    private final String reason;
    private final String correlationId;

    public MarkAppointmentNoShowCommand(UUID actorAccountId, String reason, String correlationId) {
        this.actorAccountId = actorAccountId;
        this.reason = reason;
        this.correlationId = correlationId;
    }

    public UUID actorAccountId() { return actorAccountId; }
    public String reason() { return reason; }
    public String correlationId() { return correlationId; }
}
