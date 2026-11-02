package fu.fa26.sep490.g58.otcms_be.visit.service.impl;

import fu.fa26.sep490.g58.otcms_be.common.security.*;
import fu.fa26.sep490.g58.otcms_be.visit.repository.ClinicVisitJpaRepository;
import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory;
import fu.fa26.sep490.g58.otcms_be.auth.service.PatientIdentityDirectory;
import fu.fa26.sep490.g58.otcms_be.notification.model.NotificationRelatedKind;
import fu.fa26.sep490.g58.otcms_be.notification.service.NotificationRelatedRecordResolver;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class VisitNotificationRecordResolver implements NotificationRelatedRecordResolver {
    private final ClinicVisitJpaRepository visits;
    private final PatientProfileDirectory patients;
    private final PatientIdentityDirectory identities;
    public VisitNotificationRecordResolver(ClinicVisitJpaRepository visits, PatientProfileDirectory patients, PatientIdentityDirectory identities) {
        this.visits = visits; this.patients = patients; this.identities = identities;
    }
    public NotificationRelatedKind kind() { return NotificationRelatedKind.VISIT; }
    public boolean available(AuthenticatedAccount actor, UUID id) {
        if (actor.accountRole() != AccountRole.PATIENT || !identities.isActivePatient(actor.accountId())) return false;
        var own = patients.findPatientProfile(actor.accountId());
        return own.isPresent() && visits.findById(id).filter(visit -> own.get().equals(visit.getPatientProfileId())).isPresent();
    }
}
