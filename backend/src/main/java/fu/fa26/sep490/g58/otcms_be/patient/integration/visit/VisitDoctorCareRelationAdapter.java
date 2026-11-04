package fu.fa26.sep490.g58.otcms_be.patient.integration.visit;

import fu.fa26.sep490.g58.otcms_be.auth.service.DoctorAccountAccessService;
import fu.fa26.sep490.g58.otcms_be.patient.service.DoctorCareRelationPort;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorProfileIdentityQueryService;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitDoctorCareRelationQueryService;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class VisitDoctorCareRelationAdapter implements DoctorCareRelationPort {
    private final DoctorAccountAccessService accounts;
    private final DoctorProfileIdentityQueryService identities;
    private final VisitDoctorCareRelationQueryService care;
    public VisitDoctorCareRelationAdapter(DoctorAccountAccessService accounts,
            DoctorProfileIdentityQueryService identities, VisitDoctorCareRelationQueryService care) {
        this.accounts = accounts; this.identities = identities; this.care = care;
    }
    @Override public boolean isResponsibleDoctor(UUID doctorAccountId, UUID patientProfileId) {
        if (doctorAccountId == null || patientProfileId == null || !accounts.isActiveDoctorAccount(doctorAccountId)) return false;
        return identities.findDoctorProfileId(doctorAccountId)
                .map(doctor -> care.hasCurrentCare(doctor, patientProfileId)).orElse(false);
    }
}
