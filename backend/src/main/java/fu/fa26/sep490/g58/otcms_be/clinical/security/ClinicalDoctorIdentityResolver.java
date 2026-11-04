package fu.fa26.sep490.g58.otcms_be.clinical.security;

import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorProfileIdentityQueryService;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ClinicalDoctorIdentityResolver {
    private final DoctorProfileIdentityQueryService doctors;

    public ClinicalDoctorIdentityResolver(DoctorProfileIdentityQueryService doctors) {
        this.doctors = doctors;
    }

    public UUID requireDoctorProfile(AuthenticatedAccount account) {
        if (account == null || account.accountId() == null || account.accountRole() != AccountRole.DOCTOR) {
            throw new ClinicalOperationException("MSG-40");
        }
        return doctors.findDoctorProfileId(account.accountId())
                .orElseThrow(() -> new ClinicalOperationException("MSG-40"));
    }
}
