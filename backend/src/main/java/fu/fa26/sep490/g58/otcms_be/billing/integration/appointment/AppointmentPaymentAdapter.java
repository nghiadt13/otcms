package fu.fa26.sep490.g58.otcms_be.billing.integration.appointment;

import fu.fa26.sep490.g58.otcms_be.appointment.service.AppointmentPaymentHoldQueryService;
import fu.fa26.sep490.g58.otcms_be.appointment.service.ConfirmAppointmentPaymentService;
import fu.fa26.sep490.g58.otcms_be.billing.integration.AppointmentPaymentPort;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Adapts the public appointment services to the billing port; no logic of its own. */
@Component
public class AppointmentPaymentAdapter implements AppointmentPaymentPort {
    private final ConfirmAppointmentPaymentService confirmation;
    private final AppointmentPaymentHoldQueryService holds;

    public AppointmentPaymentAdapter(ConfirmAppointmentPaymentService confirmation,
            AppointmentPaymentHoldQueryService holds) {
        this.confirmation = confirmation;
        this.holds = holds;
    }

    @Override
    public ConfirmResult confirmPaid(UUID appointmentId) {
        if (appointmentId == null) return ConfirmResult.NOT_AWAITING;
        return switch (confirmation.confirmPaid(appointmentId)) {
            case CONFIRMED -> ConfirmResult.CONFIRMED;
            case ALREADY_CONFIRMED -> ConfirmResult.ALREADY_CONFIRMED;
            case NOT_AWAITING -> ConfirmResult.NOT_AWAITING;
        };
    }

    @Override
    public Optional<Instant> liveHoldDeadline(UUID appointmentId) {
        return appointmentId == null ? Optional.empty() : holds.findLiveHoldDeadline(appointmentId);
    }

    @Override
    public Set<UUID> heldOrReleasedIds(Collection<UUID> appointmentIds) {
        return appointmentIds == null || appointmentIds.isEmpty()
                ? Set.of() : holds.findHoldOrReleasedAppointmentIds(appointmentIds);
    }
}
