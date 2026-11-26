package fu.fa26.sep490.g58.otcms_be.auth.service;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Public Auth API for resolving author labels for content records. */
public interface AccountAuthorDirectory {
    Map<UUID, String> names(Collection<UUID> accountIds);
}
