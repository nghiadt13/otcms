package fu.fa26.sep490.g58.otcms_be.auth.model.dto;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountStatus;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;

/** Internal projection; not an HTTP response. Avoids caching credential entities during JWT admission. */
public record AccountAccessSnapshot(AccountType accountType, AccountStatus status, boolean mustChangePassword) { }
