package fu.fa26.sep490.g58.otcms_be.appointment.service.impl;

import fu.fa26.sep490.g58.otcms_be.appointment.exception.AppointmentBookingException;
import fu.fa26.sep490.g58.otcms_be.appointment.model.CheckInAppointment;
import fu.fa26.sep490.g58.otcms_be.appointment.model.dto.response.ArrivalResponse;
import fu.fa26.sep490.g58.otcms_be.appointment.model.dto.response.ArrivalWorklistResponse;
import fu.fa26.sep490.g58.otcms_be.appointment.model.entity.AppointmentJpaEntity;
import fu.fa26.sep490.g58.otcms_be.appointment.repository.AppointmentCheckInRepository;
import fu.fa26.sep490.g58.otcms_be.appointment.repository.AppointmentJpaRepository;
import fu.fa26.sep490.g58.otcms_be.appointment.service.AppointmentArrivalApplicationService;
import fu.fa26.sep490.g58.otcms_be.appointment.service.ClinicArrivalProofService;
import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.config.AppointmentBookingProperties;
import fu.fa26.sep490.g58.otcms_be.visit.service.AppointmentArrivalClaimStore;
import fu.fa26.sep490.g58.otcms_be.visit.service.AppointmentArrivalClaimStore.ArrivalClaim;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentArrivalApplicationServiceImpl implements AppointmentArrivalApplicationService {
    private static final Set<String> STATES = Set.of("PENDING", "LATE", "EARLY");
    private final CoordinationGuardService guard;
    private final AppointmentCheckInRepository checkIns;
    private final AppointmentJpaRepository appointments;
    private final PatientProfileDirectory profiles;
    private final AppointmentArrivalClaimStore arrivals;
    private final ClinicArrivalProofService proofs;
    private final AppointmentBookingProperties booking;
    private final Clock clock;

    public AppointmentArrivalApplicationServiceImpl(CoordinationGuardService guard,
            AppointmentCheckInRepository checkIns, AppointmentJpaRepository appointments,
            PatientProfileDirectory profiles, AppointmentArrivalClaimStore arrivals,
            ClinicArrivalProofService proofs, AppointmentBookingProperties booking, Clock clock) {
        this.guard = guard;
        this.checkIns = checkIns;
        this.appointments = appointments;
        this.profiles = profiles;
        this.arrivals = arrivals;
        this.proofs = proofs;
        this.booking = booking;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ArrivalResponse recordQr(UUID appointmentId, UUID accountId, String proof) {
        validateIds(appointmentId, accountId);
        try {
            Instant now = Instant.now(clock);
            proofs.validate(proof, now);
            guard.acquire();
            UUID patientProfileId = profiles.findPatientProfile(accountId)
                    .orElseThrow(() -> new AppointmentBookingException("ACCESS_DENIED"));
            CheckInAppointment appointment = lock(appointmentId);
            if (!patientProfileId.equals(appointment.patientProfileId())) {
                throw new AppointmentBookingException("ACCESS_DENIED");
            }
            var existing = find(appointmentId);
            if (existing.isPresent()) {
                if (!patientProfileId.equals(existing.get().patientProfileId())) {
                    throw new AppointmentBookingException("VISIT_LINK_CONFLICT");
                }
                return response(existing.get(), appointment.startTime());
            }
            requireRecordable(appointment);
            return response(record(appointmentId, patientProfileId, accountId, "PATIENT_QR", null, now),
                    appointment.startTime());
        } catch (DataAccessException exception) {
            throw new AppointmentBookingException("DEPENDENCY_UNAVAILABLE", exception);
        }
    }

    @Override
    @Transactional
    public ArrivalResponse recordManual(UUID appointmentId, UUID accountId, String reason) {
        validateIds(appointmentId, accountId);
        String cleanReason = reason == null ? "" : reason.trim();
        if (cleanReason.isEmpty() || cleanReason.length() > 500) {
            throw new AppointmentBookingException("VALIDATION_FAILED");
        }
        try {
            guard.acquire();
            CheckInAppointment appointment = lock(appointmentId);
            var existing = find(appointmentId);
            if (existing.isPresent()) {
                if (!appointment.patientProfileId().equals(existing.get().patientProfileId())) {
                    throw new AppointmentBookingException("VISIT_LINK_CONFLICT");
                }
                return response(existing.get(), appointment.startTime());
            }
            requireRecordable(appointment);
            Instant now = Instant.now(clock);
            return response(record(appointmentId, appointment.patientProfileId(), accountId,
                    "RECEPTIONIST", cleanReason, now), appointment.startTime());
        } catch (DataAccessException exception) {
            throw new AppointmentBookingException("DEPENDENCY_UNAVAILABLE", exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ArrivalWorklistResponse list(LocalDate clinicDate, String requestedState, int page, int size) {
        if (clinicDate == null || page < 0 || size < 1 || size > 100
                || (requestedState != null && !STATES.contains(requestedState))) {
            throw new AppointmentBookingException("VALIDATION_FAILED");
        }
        Instant from = clinicDate.atStartOfDay(booking.clinicTimeZone()).toInstant();
        Instant to = clinicDate.plusDays(1).atStartOfDay(booking.clinicTimeZone()).toInstant();
        try {
            List<ArrivalResponse> matching = new ArrayList<>();
            int scanPage = 0;
            int scanSize = 500;
            long requiredMatches = ((long) page * size) + size + 1;
            List<ArrivalClaim> claims;
            do {
                claims = arrivals.list(from, to, scanPage++, scanSize);
                for (ArrivalClaim claim : claims) {
                    AppointmentJpaEntity appointment = appointments.findById(claim.appointmentId()).orElse(null);
                    if (appointment == null || !appointment.getPatientProfileId().equals(claim.patientProfileId())) continue;
                    String state = state(claim.arrivedAt(), appointment.getStartTime());
                    if (!"LATE".equals(state)
                            && (!"CONFIRMED".equals(appointment.getStatus()) || appointment.getVisitId() != null)) continue;
                    if (requestedState != null && !requestedState.equals(state)) continue;
                    matching.add(new ArrivalResponse(claim.appointmentId(), claim.patientProfileId(), claim.source(),
                            state, claim.arrivedAt(), claim.recordedAt()));
                    if (matching.size() >= requiredMatches) break;
                }
            } while (claims.size() == scanSize && matching.size() < requiredMatches);
            long offset = (long) page * size;
            List<ArrivalResponse> items = matching.stream().skip(offset).limit(size).toList();
            boolean hasMore = matching.size() > offset + size;
            return new ArrivalWorklistResponse(clinicDate, page, size, hasMore, List.copyOf(items));
        } catch (DataAccessException exception) {
            throw new AppointmentBookingException("DEPENDENCY_UNAVAILABLE", exception);
        }
    }

    private CheckInAppointment lock(UUID appointmentId) {
        try {
            return checkIns.findForUpdate(appointmentId)
                    .orElseThrow(() -> new AppointmentBookingException("APPOINTMENT_NOT_FOUND"));
        } catch (DataAccessException exception) {
            throw new AppointmentBookingException("DEPENDENCY_UNAVAILABLE", exception);
        }
    }

    private ArrivalClaim record(UUID appointmentId, UUID patientId, UUID actorId, String source,
            String reason, Instant now) {
        try {
            return arrivals.record(appointmentId, patientId, actorId, source, reason, now);
        } catch (VisitOperationException exception) {
            throw new AppointmentBookingException(exception.code(), exception);
        } catch (DataAccessException exception) {
            throw new AppointmentBookingException("DEPENDENCY_UNAVAILABLE", exception);
        }
    }

    private java.util.Optional<ArrivalClaim> find(UUID appointmentId) {
        try {
            return arrivals.find(appointmentId);
        } catch (VisitOperationException exception) {
            throw new AppointmentBookingException(exception.code(), exception);
        } catch (DataAccessException exception) {
            throw new AppointmentBookingException("DEPENDENCY_UNAVAILABLE", exception);
        }
    }

    private void requireRecordable(CheckInAppointment appointment) {
        if (!"CONFIRMED".equals(appointment.status()) || appointment.visitId() != null) {
            throw new AppointmentBookingException("APPOINTMENT_NOT_CHANGEABLE");
        }
    }

    private ArrivalResponse response(ArrivalClaim claim, Instant appointmentStart) {
        return new ArrivalResponse(claim.appointmentId(), claim.patientProfileId(), claim.source(),
                state(claim.arrivedAt(), appointmentStart), claim.arrivedAt(), claim.recordedAt());
    }

    private String state(Instant arrivedAt, Instant appointmentStart) {
        Instant early = appointmentStart.minus(booking.checkInEarlyMinutes(), ChronoUnit.MINUTES);
        Instant late = appointmentStart.plus(booking.checkInLateGraceMinutes(), ChronoUnit.MINUTES);
        if (arrivedAt.isBefore(early)) return "EARLY";
        return arrivedAt.isAfter(late) ? "LATE" : "PENDING";
    }

    private static void validateIds(UUID appointmentId, UUID accountId) {
        if (appointmentId == null || accountId == null) throw new AppointmentBookingException("VALIDATION_FAILED");
    }
}
