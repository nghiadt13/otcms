package fu.fa26.sep490.g58.otcms_be.auth.service;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Public Auth API for the staff-profile feature. */
public interface StaffAccountDirectory {
    record Entry(UUID id, String username, String fullName, AccountType role) { }
    List<Entry> activeStaff();
    Optional<Entry> activeStaff(UUID id);
    Optional<Entry> staff(UUID id);
}
