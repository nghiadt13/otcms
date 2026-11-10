package fu.fa26.sep490.g58.otcms_be.appointment.service.impl;

import fu.fa26.sep490.g58.otcms_be.appointment.repository.AppointmentPaymentHoldRepository;
import fu.fa26.sep490.g58.otcms_be.appointment.service.AppointmentPaymentHoldQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class AppointmentPaymentHoldQueryServiceImpl implements AppointmentPaymentHoldQueryService {
    private final AppointmentPaymentHoldRepository holds;

    public AppointmentPaymentHoldQueryServiceImpl(AppointmentPaymentHoldRepository holds) {
        this.holds = holds;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Instant> findLiveHoldDeadline(UUID appointmentId) {
        if (appointmentId == null) return Optional.empty();
        return holds.findLiveHoldDeadline(appointmentId);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> findHoldOrReleasedAppointmentIds(Collection<UUID> appointmentIds) {
        return holds.holdOrReleasedAmong(appointmentIds);
    }
}
