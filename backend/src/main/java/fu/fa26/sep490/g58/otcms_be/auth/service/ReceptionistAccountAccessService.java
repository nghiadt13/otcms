package fu.fa26.sep490.g58.otcms_be.auth.service;

import java.util.UUID;

/** Auth-owned decision for current Receptionist access. */
public interface ReceptionistAccountAccessService {
    boolean isActiveReceptionistAccount(UUID accountId);
}
