package fu.fa26.sep490.g58.otcms_be.auth.service;

import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import java.util.*;

public interface NotificationRecipientDirectory {
    boolean active(UUID accountId);
    Optional<UUID> patientAccount(UUID patientProfileId);
    List<UUID> activeAccounts(AccountRole role);
}
