package fu.fa26.sep490.g58.otcms_be.auth.service;

import java.util.UUID;

/** Auth-owned projection used when Staff links an existing account to a Doctor profile. */
public interface DoctorAccountTypeQueryService {
    boolean isDoctorAccount(UUID accountId);
}
