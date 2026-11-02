package fu.fa26.sep490.g58.otcms_be.auth.service;

import java.util.UUID;

/** Public current identity projection; no entity or demographic data crosses the boundary. */
public interface PatientIdentityDirectory {
    boolean isActivePatient(UUID accountId);
}
