package fu.fa26.sep490.g58.otcms_be.visit.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.service.ReceptionistAccountAccessService;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.notification.model.NotificationMessages;
import fu.fa26.sep490.g58.otcms_be.notification.service.NotificationPublisher;
import fu.fa26.sep490.g58.otcms_be.patient.service.PatientClinicalIdentityQueryService;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorProfileIdentityQueryService;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.model.dto.response.VisitLifecycleDtos.VisitQueueTicket;
import fu.fa26.sep490.g58.otcms_be.visit.repository.ClinicVisitJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitLifecycleRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.AttendingDoctorTransferService;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitExamQueueService;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AttendingDoctorTransferServiceImpl implements AttendingDoctorTransferService {
    private final ReceptionistAccountAccessService receptionists;
    private final CoordinationGuardService guard;
    private final ClinicVisitJpaRepository visits;
    private final VisitLifecycleRepository lifecycle;
    private final VisitExamQueueService examQueue;
    private final DoctorProfileIdentityQueryService doctorProfiles;
    private final PatientClinicalIdentityQueryService identities;
    private final NotificationPublisher notifications;
    private final VisitAuditRecorder audit;
    private final Clock clock;

    public AttendingDoctorTransferServiceImpl(ReceptionistAccountAccessService receptionists,
            CoordinationGuardService guard, ClinicVisitJpaRepository visits, VisitLifecycleRepository lifecycle,
            VisitExamQueueService examQueue, DoctorProfileIdentityQueryService doctorProfiles,
            PatientClinicalIdentityQueryService identities, NotificationPublisher notifications,
            VisitAuditRecorder audit, Clock clock) {
        this.receptionists = receptionists;
        this.guard = guard;
        this.visits = visits;
        this.lifecycle = lifecycle;
        this.examQueue = examQueue;
        this.doctorProfiles = doctorProfiles;
        this.identities = identities;
        this.notifications = notifications;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    @Transactional
    public VisitQueueTicket transfer(UUID visitId, UUID newDoctorProfileId, String reason,
            AuthenticatedAccount actor, String correlationId) {
        if (actor == null || actor.accountId() == null || actor.accountRole() != AccountRole.RECEPTIONIST
                || !receptionists.isActiveReceptionistAccount(actor.accountId())) {
            throw new VisitOperationException("MSG-40");
        }
        if (visitId == null || newDoctorProfileId == null || reason == null || reason.isBlank()) {
            throw new VisitOperationException("MSG-39");
        }
        guard.acquire();
        var visit = visits.findByIdForUpdate(visitId).orElseThrow(() -> new VisitOperationException("MSG-41"));
        if (!"OPEN".equals(visit.getStatus())) throw new VisitOperationException("MSG-42");
        Instant now = clock.instant();
        UUID previousDoctor = examQueue.find(visitId).orElseThrow(() -> new VisitOperationException("MSG-42")).doctorProfileId();
        var ticket = examQueue.reassignDoctor(visitId, newDoctorProfileId, now);
        lifecycle.reassignOpenCare(visitId, newDoctorProfileId);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("fromDoctorProfileId", previousDoctor.toString());
        metadata.put("toDoctorProfileId", newDoctorProfileId.toString());
        metadata.put("reason", reason.trim());
        audit.lifecycleEvent("ATTENDING_DOCTOR_TRANSFERRED", "VISIT", visitId, visitId, actor.accountId(), now,
                metadata, correlationId);

        var identity = identities.findClinicalIdentities(List.of(visit.getPatientProfileId()))
                .get(visit.getPatientProfileId());
        // The visit has no patient code in this schema, so the patient's name stands in for it in MSG-83.
        String patientRef = identity == null ? visit.getPatientProfileId().toString() : identity.fullName();
        doctorProfiles.findDoctorAccountId(newDoctorProfileId).ifPresent(accountId -> notifications.publish(accountId,
                NotificationMessages.visitChanged(visitId, patientRef, "bệnh nhân được chuyển cho bạn phụ trách")));
        return new VisitQueueTicket(visitId, examQueue.find(visitId).map(entry -> entry.examStatus().name()).orElse(null),
                ticket.queueNumber(), ticket.doctorProfileId(), ticket.doctorName(), ticket.roomId(), ticket.roomLabel());
    }
}
