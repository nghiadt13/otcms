package fu.fa26.sep490.g58.otcms_be.visit.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.service.ReceptionistAccountAccessService;
import fu.fa26.sep490.g58.otcms_be.auth.service.DoctorAccountAccessService;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.repository.ClinicVisitJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.repository.WalkInReceptionQueueEntryJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.ExitWalkInReceptionQueueCommand;
import fu.fa26.sep490.g58.otcms_be.visit.service.ExitWalkInReceptionQueueService;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExitWalkInReceptionQueueServiceImpl implements ExitWalkInReceptionQueueService {
    private static final Set<String> DOCTOR_REASONS = Set.of("CARE_STARTED", "VISIT_CLOSED");
    private final WalkInReceptionQueueEntryJpaRepository entries;
    private final ClinicVisitJpaRepository visits;
    private final ReceptionistAccountAccessService receptionists;
    private final DoctorAccountAccessService doctors;

    public ExitWalkInReceptionQueueServiceImpl(WalkInReceptionQueueEntryJpaRepository entries,
            ClinicVisitJpaRepository visits,
            ReceptionistAccountAccessService receptionists, DoctorAccountAccessService doctors) {
        this.entries = entries;
        this.visits = visits;
        this.receptionists = receptionists;
        this.doctors = doctors;
    }

    @Override
    @Transactional
    public void exit(ExitWalkInReceptionQueueCommand command) {
        if (command == null || command.visitId() == null || command.actorAccountId() == null
                || command.actorRole() == null || command.occurredAt() == null
                || command.correlationId() == null || command.correlationId().isBlank()
                || command.correlationId().length() > 128
                || command.reason() == null
                || !Set.of("CARE_STARTED", "CANCELLED", "VISIT_CLOSED").contains(command.reason())) {
            throw new VisitOperationException("VALIDATION_FAILED");
        }
        boolean doctorAction = DOCTOR_REASONS.contains(command.reason());
        if (doctorAction) {
            if (command.actorRole() != AccountRole.DOCTOR
                    || !doctors.isActiveDoctorAccount(command.actorAccountId())) {
                throw new VisitOperationException("ACCESS_DENIED");
            }
        } else if (command.actorRole() != AccountRole.RECEPTIONIST
                || !receptionists.isActiveReceptionistAccount(command.actorAccountId())) {
            throw new VisitOperationException("ACCESS_DENIED");
        }

        var visit = visits.findByIdForUpdate(command.visitId())
                .orElseThrow(() -> new VisitOperationException("RESOURCE_NOT_FOUND"));
        if (!"WALK_IN".equals(visit.getSource()) || !"OPEN".equals(visit.getStatus())) {
            throw new VisitOperationException("VISIT_RECEPTION_QUEUE_CONFLICT");
        }

        entries.setAuditActor(command.actorAccountId().toString());
        entries.setAuditCorrelation(command.correlationId());
        for (var entryId : entries.findWaitingEntryIdsForUpdate(command.visitId())) {
            if (entries.leaveReception(entryId, command.occurredAt(), command.reason()) != 1) {
                throw new VisitOperationException("VISIT_RECEPTION_QUEUE_CONFLICT");
            }
        }
    }
}
