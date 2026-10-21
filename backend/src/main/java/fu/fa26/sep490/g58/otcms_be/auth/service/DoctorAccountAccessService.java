package fu.fa26.sep490.g58.otcms_be.auth.service;

import java.util.UUID;

/** Auth-owned current access decision for Doctor operations; account status itself does not cross the boundary. */
public interface DoctorAccountAccessService {
    boolean isActiveDoctorAccount(UUID accountId);
}
