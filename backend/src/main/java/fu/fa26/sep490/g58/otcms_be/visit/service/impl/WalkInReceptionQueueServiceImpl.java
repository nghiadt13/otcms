package fu.fa26.sep490.g58.otcms_be.visit.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.service.ReceptionistAccountAccessService;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.model.dto.response.WalkInReceptionQueueEntryResponse;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.ClinicVisitJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.WalkInReceptionQueueEntryJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.repository.ClinicVisitJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.repository.WalkInReceptionQueueEntryJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.CreateWalkInReceptionQueueEntryCommand;
import fu.fa26.sep490.g58.otcms_be.visit.service.WalkInReceptionQueueService;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalkInReceptionQueueServiceImpl implements WalkInReceptionQueueService {
    private final ClinicVisitJpaRepository visits;
    private final WalkInReceptionQueueEntryJpaRepository entries;
    private final ReceptionistAccountAccessService receptionistAccess;
    private final Clock clock;

    public WalkInReceptionQueueServiceImpl(ClinicVisitJpaRepository visits,
            WalkInReceptionQueueEntryJpaRepository entries, ReceptionistAccountAccessService receptionistAccess,
            Clock clock) {
        this.visits = visits;
        this.entries = entries;
        this.receptionistAccess = receptionistAccess;
        this.clock = clock;
    }

    @Override
    @Transactional
    public WalkInReceptionQueueEntryResponse createWaitingEntry(CreateWalkInReceptionQueueEntryCommand command) {
        validate(command);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedAccount principal)
                || principal.accountId() == null || principal.sessionId() == null
                || principal.accountRole() != AccountRole.RECEPTIONIST
                || !principal.accountId().equals(command.actor().accountId())
                || principal.accountRole() != command.actor().accountRole()
                || !principal.sessionId().equals(command.actor().sessionId())
                || !receptionistAccess.isActiveReceptionistAccount(principal.accountId())) {
            throw new VisitOperationException("ACCESS_DENIED");
        }

        ClinicVisitJpaEntity visit = visits.findByIdForUpdate(command.visitId())
                .orElseThrow(() -> new VisitOperationException("RESOURCE_NOT_FOUND"));
        if (!"WALK_IN".equals(visit.getSource()) || !"OPEN".equals(visit.getStatus())) {
            throw new VisitOperationException("VISIT_NOT_QUEUEABLE");
        }

        Instant enteredAt = clock.instant();
        entries.setAuditActor(principal.accountId().toString());
        entries.setAuditCorrelation(command.correlationId() == null ? "" : command.correlationId());
        WalkInReceptionQueueEntryJpaEntity entry;
        try {
            entry = entries.saveAndFlush(
                    WalkInReceptionQueueEntryJpaEntity.waiting(visit.getId(), visit.getArrivedAt(), enteredAt));
        } catch (DataIntegrityViolationException exception) {
            if (hasConstraint(exception, "uk_walk_in_reception_queue_active_visit")) {
                throw new VisitOperationException("QUEUE_ENTRY_ALREADY_ACTIVE", exception);
            }
            throw exception;
        }
        return new WalkInReceptionQueueEntryResponse(entry.getId(), entry.getVisitId(), entry.getArrivedAt(),
                entry.getStatus(), entry.getEnteredAt());
    }

    private static void validate(CreateWalkInReceptionQueueEntryCommand command) {
        if (command == null || command.visitId() == null || command.actor() == null
                || command.actor().accountId() == null || command.actor().accountRole() == null
                || (command.correlationId() != null && (command.correlationId().isBlank()
                        || command.correlationId().length() > 128))) {
            throw new VisitOperationException("VALIDATION_FAILED");
        }
    }

    private static boolean hasConstraint(Throwable error, String expectedConstraint) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof ConstraintViolationException violation
                    && expectedConstraint.equals(violation.getConstraintName())) return true;
            if (current instanceof SQLException sql) {
                for (SQLException next = sql; next != null; next = next.getNextException()) {
                    if (next.getMessage() != null && next.getMessage().contains(expectedConstraint)) return true;
                }
            }
        }
        return false;
    }
}
