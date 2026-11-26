package fu.fa26.sep490.g58.otcms_be.visit.service.impl;
import fu.fa26.sep490.g58.otcms_be.auth.service.ReceptionistAccountAccessService;
import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.patient.service.PatientClinicalIdentityQueryService;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.model.dto.response.ReceptionVisitPage;
import fu.fa26.sep490.g58.otcms_be.visit.repository.ClinicVisitJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.ReceptionVisitQueryService;
import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class ReceptionVisitQueryServiceImpl implements ReceptionVisitQueryService {
    private final ClinicVisitJpaRepository visits;
    private final PatientClinicalIdentityQueryService patients;
    private final ReceptionistAccountAccessService receptionists;
    private final PatientProfileDirectory profiles;

    public ReceptionVisitQueryServiceImpl(ClinicVisitJpaRepository visits, PatientClinicalIdentityQueryService patients,
            ReceptionistAccountAccessService receptionists) {
        this(visits, patients, receptionists, null);
    }

    @Autowired
    public ReceptionVisitQueryServiceImpl(ClinicVisitJpaRepository visits, PatientClinicalIdentityQueryService patients,
            ReceptionistAccountAccessService receptionists, PatientProfileDirectory profiles) {
        this.visits = visits; this.patients = patients; this.receptionists = receptionists; this.profiles = profiles;
    }
    @Override @Transactional(readOnly = true)
    public ReceptionVisitPage list(AuthenticatedAccount actor, UUID patientId, String status, int page, int size) {
        if (actor == null || actor.accountId() == null || (actor.accountRole() != AccountRole.ADMIN
                && (actor.accountRole() != AccountRole.RECEPTIONIST
                || !receptionists.isActiveReceptionistAccount(actor.accountId())))) throw new VisitOperationException("MSG-40");
        if (page < 0 || size < 1 || size > 100 || (status != null
                && !Set.of("OPEN", "CLOSED", "INCOMPLETE").contains(status)))
            throw new VisitOperationException("VALIDATION_FAILED");
        var result = visits.findReceptionVisits(patientId, status, PageRequest.of(page, size));
        var patientIds = result.stream().map(v -> v.getPatientProfileId()).toList();
        var identities = patients.findClinicalIdentities(patientIds);
        Map<UUID, String> patientCodes = profiles == null ? Map.of() : profiles.findProfiles(patientIds).stream()
                .filter(profile -> profile.patientCode() != null)
                .collect(Collectors.toMap(PatientProfileDirectory.ProfileRecord::id,
                        PatientProfileDirectory.ProfileRecord::patientCode, (first, ignored) -> first));
        var items = result.stream().map(v -> {
            var identity = identities.get(v.getPatientProfileId());
            return new ReceptionVisitPage.Item(v.getId(), v.getPatientProfileId(),
                    identity == null ? null : identity.fullName(), v.getStatus(), v.getArrivedAt(),
                    patientCodes.get(v.getPatientProfileId()));
        }).toList();
        return new ReceptionVisitPage(page, result.hasNext(), items);
    }
}
