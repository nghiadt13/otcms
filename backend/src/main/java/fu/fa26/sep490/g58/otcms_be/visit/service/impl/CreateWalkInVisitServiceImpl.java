package fu.fa26.sep490.g58.otcms_be.visit.service.impl;

import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.exception.ClinicServiceException;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicServiceType;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.GetClinicServiceCatalogService;
import fu.fa26.sep490.g58.otcms_be.staff.exception.DoctorProfileException;
import fu.fa26.sep490.g58.otcms_be.staff.model.DoctorWorkShift;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorProfileIdentityQueryService;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorScheduleService;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.model.CreatedWalkInVisit;
import fu.fa26.sep490.g58.otcms_be.visit.model.ExamQueueEntry;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.ClinicVisitJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.VisitAuditJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.VisitInitialReceptionJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.VisitRoomJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.WalkInReceptionQueueEntryJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.WalkInReceptionReplayJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.repository.ClinicVisitJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitAuditJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitInitialReceptionJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitRoomJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.repository.WalkInReceptionQueueEntryJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.repository.WalkInReceptionReplayJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.CreateWalkInVisitCommand;
import fu.fa26.sep490.g58.otcms_be.visit.service.CreateWalkInVisitService;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitExamQueueService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
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
    private final WalkInReceptionQueueEntryJpaRepository queueEntries;
    private final VisitExamQueueService examQueue;
    private final DoctorScheduleService schedules;
    private final DoctorProfileIdentityQueryService doctorProfiles;
    private final GetClinicServiceCatalogService catalog;
    private final VisitRoomJpaRepository rooms;
    private final Clock clock;

    public CreateWalkInVisitServiceImpl(CoordinationGuardService coordinationGuard, ClinicVisitJpaRepository visits,
            VisitInitialReceptionJpaRepository receptions, VisitAuditJpaRepository audits,
            WalkInReceptionReplayJpaRepository replays, WalkInReceptionQueueEntryJpaRepository queueEntries,
            VisitExamQueueService examQueue, DoctorScheduleService schedules,
            DoctorProfileIdentityQueryService doctorProfiles, GetClinicServiceCatalogService catalog,
            VisitRoomJpaRepository rooms, Clock clock) {
        this.coordinationGuard = coordinationGuard;
        this.visits = visits;
        this.receptions = receptions;
        this.audits = audits;
        this.replays = replays;
        this.queueEntries = queueEntries;
        this.examQueue = examQueue;
        this.schedules = schedules;
        this.doctorProfiles = doctorProfiles;
        this.catalog = catalog;
        this.rooms = rooms;
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
        String fingerprint = fingerprint(command);

        coordinationGuard.acquire();

        var replay = replays.findByReceptionistAccountIdAndIdempotencyKey(command.actorAccountId(), key);
        if (replay.isPresent()) {
            var stored = replay.get();
            if (!fingerprint.equals(stored.getRequestFingerprint())) {
                throw new VisitOperationException("IDEMPOTENCY_CONFLICT");
            }
            return visits.findById(stored.getVisitId()).map(this::result)
                    .orElseThrow(() -> new VisitOperationException("VISIT_LINK_CONFLICT"));
        }

        Instant arrivedAt = clock.instant();
        requireDoctorOnShift(command.doctorProfileId(), arrivedAt);
        BigDecimal fee = requireExaminationFee(command.serviceId());

        UUID visitId = UUID.randomUUID();
        ClinicVisitJpaEntity visit;
        try {
            visit = visits.saveAndFlush(ClinicVisitJpaEntity.walkIn(visitId, command.patientProfileId(),
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
        queueEntries.setAuditActor(command.actorAccountId().toString());
        queueEntries.setAuditCorrelation(command.correlationId());
        queueEntries.saveAndFlush(WalkInReceptionQueueEntryJpaEntity.waiting(visitId, arrivedAt, arrivedAt));
        // A fee walk-in waits unnumbered until billing reports the fee paid; a free one is numbered at once.
        if (fee.signum() > 0) {
            examQueue.createPendingPayment(visitId, command.doctorProfileId(), command.serviceId(), arrivedAt);
        } else {
            examQueue.enqueueWaiting(visitId, command.doctorProfileId(), command.serviceId(), arrivedAt);
        }
        replays.saveAndFlush(WalkInReceptionReplayJpaEntity.create(command.actorAccountId(), key, fingerprint,
                visitId));

        return result(visit);
    }

    /** Response built from the stored queue row, so a replay shows the walk-in's current state. */
    private CreatedWalkInVisit result(ClinicVisitJpaEntity visit) {
        ExamQueueEntry entry = examQueue.find(visit.getId())
                .orElseThrow(() -> new VisitOperationException("VISIT_LINK_CONFLICT"));
        UUID doctorProfileId = entry.doctorProfileId();
        String doctorName = doctorProfiles.findDoctorNames(List.of(doctorProfileId)).get(doctorProfileId);
        String roomLabel = entry.roomId() == null ? null
                : rooms.findById(entry.roomId()).map(VisitRoomJpaEntity::getDisplayLabel).orElse(null);
        BigDecimal fee = catalog.getService(entry.serviceId()).getPrice();
        return new CreatedWalkInVisit(visit.getId(), visit.getPatientProfileId(), visit.getPriorAppointmentId(),
                visit.getArrivedAt(), entry.examStatus(), entry.queueNumber(), doctorName, roomLabel, fee);
    }

    /** DOCTOR_NOT_ON_SHIFT (MSG-04) when no shift covers now. An unknown doctor profile is treated the same way. */
    private void requireDoctorOnShift(UUID doctorProfileId, Instant now) {
        List<DoctorWorkShift> shifts;
        try {
            shifts = schedules.forProfile(doctorProfileId, now, now.plusSeconds(1));
        } catch (DoctorProfileException unknownDoctor) {
            shifts = List.of();
        }
        if (shifts.isEmpty()) {
            throw new VisitOperationException("DOCTOR_NOT_ON_SHIFT");
        }
    }

    /** The walk-in service must be active and EXAMINATION; returns its price (0 means a free walk-in). */
    private BigDecimal requireExaminationFee(UUID serviceId) {
        ClinicService service;
        try {
            service = catalog.getService(serviceId);
        } catch (ClinicServiceException unknownService) {
            throw new VisitOperationException("VALIDATION_FAILED", unknownService);
        }
        if (!service.isActive() || service.getType() != ClinicServiceType.EXAMINATION) {
            throw new VisitOperationException("VALIDATION_FAILED");
        }
        return service.getPrice();
    }

    private static void validate(CreateWalkInVisitCommand command) {
        if (command == null || command.actorAccountId() == null || command.actorRole() == null
                || command.patientProfileId() == null || command.doctorProfileId() == null
                || command.serviceId() == null || command.idempotencyKey() == null
                || command.idempotencyKey().isBlank() || command.idempotencyKey().trim().length() > 128
                || command.correlationId() == null || command.correlationId().isBlank()
                || command.correlationId().length() > 128) {
            throw new VisitOperationException("VALIDATION_FAILED");
        }
    }

    private static String fingerprint(CreateWalkInVisitCommand command) {
        String canonicalRequest = "visit-walk-in-v2\n" + command.actorAccountId() + "\n"
                + command.patientProfileId() + "\n"
                + (command.priorAppointmentId() == null ? "" : command.priorAppointmentId().toString()) + "\n"
                + command.doctorProfileId() + "\n" + command.serviceId();
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
