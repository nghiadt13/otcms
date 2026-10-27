package fu.fa26.sep490.g58.otcms_be.visit.service.impl;

import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.model.CreatedWalkInVisit;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.ClinicVisitJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.VisitAuditJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.VisitInitialReceptionJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.WalkInReceptionReplayJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.repository.ClinicVisitJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitAuditJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitInitialReceptionJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.repository.WalkInReceptionReplayJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.CreateWalkInVisitCommand;
import fu.fa26.sep490.g58.otcms_be.visit.service.CreateWalkInVisitService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateWalkInVisitServiceImpl implements CreateWalkInVisitService {
    private static final String PATIENT_FK = "clinic_visits_patient_profile_id_fkey";
    private static final String PRIOR_APPOINTMENT_FK = "fk_clinic_visits_prior_appointment_patient";

    private final CoordinationGuardService coordinationGuard;
    private final ClinicVisitJpaRepository visits;
    private final VisitInitialReceptionJpaRepository receptions;
    private final VisitAuditJpaRepository audits;
    private final WalkInReceptionReplayJpaRepository replays;
    private final Clock clock;

    public CreateWalkInVisitServiceImpl(CoordinationGuardService coordinationGuard, ClinicVisitJpaRepository visits,
            VisitInitialReceptionJpaRepository receptions, VisitAuditJpaRepository audits,
            WalkInReceptionReplayJpaRepository replays, Clock clock) {
        this.coordinationGuard = coordinationGuard;
        this.visits = visits;
        this.receptions = receptions;
        this.audits = audits;
        this.replays = replays;
        this.clock = clock;
    }

    @Override
    @Transactional
    public CreatedWalkInVisit create(CreateWalkInVisitCommand command) {
        validate(command);
        if (command.actorRole() != AccountRole.RECEPTIONIST) {
            throw new VisitOperationException("ACCESS_DENIED");
        }

        String key = command.idempotencyKey().trim();
        String fingerprint = fingerprint(command.actorAccountId(), command.patientProfileId(),
                command.priorAppointmentId());

        coordinationGuard.acquire();

        var replay = replays.findByReceptionistAccountIdAndIdempotencyKey(command.actorAccountId(), key);
        if (replay.isPresent()) {
            var stored = replay.get();
            if (!fingerprint.equals(stored.getRequestFingerprint())) {
                throw new VisitOperationException("IDEMPOTENCY_CONFLICT");
            }
            return visits.findById(stored.getVisitId()).map(CreateWalkInVisitServiceImpl::toResult)
                    .orElseThrow(() -> new VisitOperationException("VISIT_LINK_CONFLICT"));
        }

        Instant arrivedAt = clock.instant();
        UUID visitId = UUID.randomUUID();
        try {
            visits.saveAndFlush(ClinicVisitJpaEntity.walkIn(visitId, command.patientProfileId(),
                    command.priorAppointmentId(), command.actorAccountId(), arrivedAt));
        } catch (DataIntegrityViolationException exception) {
            if (hasConstraint(exception, PATIENT_FK) || hasConstraint(exception, PRIOR_APPOINTMENT_FK)) {
                throw new VisitOperationException("RESOURCE_NOT_FOUND", exception);
            }
            throw exception;
        }

        receptions.save(VisitInitialReceptionJpaEntity.walkIn(visitId, command.actorAccountId(),
                command.priorAppointmentId(), arrivedAt));
        audits.save(VisitAuditJpaEntity.walkInCreated(command.actorAccountId(), visitId, arrivedAt,
                command.correlationId()));
        replays.saveAndFlush(WalkInReceptionReplayJpaEntity.create(command.actorAccountId(), key, fingerprint,
                visitId));

        return new CreatedWalkInVisit(visitId, command.patientProfileId(), command.priorAppointmentId(), arrivedAt);
    }

    private static CreatedWalkInVisit toResult(ClinicVisitJpaEntity visit) {
        return new CreatedWalkInVisit(visit.getId(), visit.getPatientProfileId(), visit.getPriorAppointmentId(),
                visit.getArrivedAt());
    }

    private static void validate(CreateWalkInVisitCommand command) {
        if (command == null || command.actorAccountId() == null || command.actorRole() == null
                || command.patientProfileId() == null || command.idempotencyKey() == null
                || command.idempotencyKey().isBlank() || command.idempotencyKey().trim().length() > 128
                || command.correlationId() == null || command.correlationId().isBlank()
                || command.correlationId().length() > 128) {
            throw new VisitOperationException("VALIDATION_FAILED");
        }
    }

    private static String fingerprint(UUID actorAccountId, UUID patientProfileId, UUID priorAppointmentId) {
        String canonicalRequest = "visit-walk-in-v1\n" + actorAccountId + "\n" + patientProfileId + "\n"
                + (priorAppointmentId == null ? "" : priorAppointmentId.toString());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonicalRequest.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new ExceptionInInitializerError(impossible);
        }
    }

    private static boolean hasConstraint(Throwable error, String expectedConstraint) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof ConstraintViolationException violation
                    && expectedConstraint.equals(violation.getConstraintName())) return true;
            if (current instanceof java.sql.SQLException sql) {
                for (java.sql.SQLException next = sql; next != null; next = next.getNextException()) {
                    if (next.getMessage() != null && next.getMessage().contains(expectedConstraint)) return true;
                }
            }
        }
        return false;
    }
}
