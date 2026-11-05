package fu.fa26.sep490.g58.otcms_be.clinical.service;

import fu.fa26.sep490.g58.otcms_be.clinical.model.dto.DoctorActiveVisitDtos.ActiveVisits;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;

public interface DoctorActiveVisitReadService {
    ActiveVisits findActiveVisits(AuthenticatedAccount account);
}
