package fu.fa26.sep490.g58.otcms_be.appointment.service.impl;

import fu.fa26.sep490.g58.otcms_be.appointment.exception.AppointmentBookingException;
import fu.fa26.sep490.g58.otcms_be.appointment.repository.AppointmentPaymentHoldRepository;
import fu.fa26.sep490.g58.otcms_be.appointment.service.CancelPendingPaymentHoldService;
import fu.fa26.sep490.g58.otcms_be.appointment.service.ReleaseAppointmentPaymentHoldService;
import fu.fa26.sep490.g58.otcms_be.appointment.service.ReleaseAppointmentPaymentHoldService.Actor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Not transactional on purpose: each hold is settled with billing first and then released in its own transaction
 * (invoice lock before appointment lock), so a gateway call never runs inside an appointment row lock.
 */
@Service
public class CancelPendingPaymentHoldServiceImpl implements CancelPendingPaymentHoldService {
    private static final Logger log = LoggerFactory.getLogger(CancelPendingPaymentHoldServiceImpl.class);
    static final String LEFT_PAYMENT_REASON = "Bệnh nhân hủy giữ chỗ thanh toán";

    private final AppointmentPaymentHoldRepository holds;
    private final ReleaseAppointmentPaymentHoldService release;

    public CancelPendingPaymentHoldServiceImpl(AppointmentPaymentHoldRepository holds,
            ReleaseAppointmentPaymentHoldService release) {
        this.holds = holds;
        this.release = release;
    }

    @Override
    public Result cancelAll(UUID accountId, String correlationId) {
        if (accountId == null || correlationId == null || correlationId.isBlank() || correlationId.length() > 128) {
            throw new AppointmentBookingException("VALIDATION_FAILED");
        }
        int cancelled = 0;
        boolean alreadyPaid = false;
        RuntimeException failure = null;
        for (UUID id : holds.findHoldIdsByCreator(accountId)) {
            try {
                switch (release.release(id, true, Actor.patient(accountId, LEFT_PAYMENT_REASON, correlationId))) {
                    case RELEASED -> cancelled++;
                    case PAID -> alreadyPaid = true;
                    case STILL_PENDING, NOT_AWAITING -> { /* force=true never pends; NOT_AWAITING = changed concurrently */ }
                }
            } catch (RuntimeException ex) {
                log.warn("Cancelling payment hold {} failed", id, ex);
                failure = ex;
            }
        }
        if (failure != null && cancelled == 0 && !alreadyPaid) {
            throw new AppointmentBookingException("DEPENDENCY_UNAVAILABLE", failure);
        }
        return new Result(cancelled, alreadyPaid);
    }
}
