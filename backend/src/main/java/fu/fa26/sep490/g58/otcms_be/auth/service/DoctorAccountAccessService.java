package fu.fa26.sep490.g58.otcms_be.auth.service;

import java.util.UUID;

public interface DoctorAccountAccessService {
    boolean isActiveDoctorAccount(UUID accountId);
}
