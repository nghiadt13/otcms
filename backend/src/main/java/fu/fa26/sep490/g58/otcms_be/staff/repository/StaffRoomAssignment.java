package fu.fa26.sep490.g58.otcms_be.staff.repository;

import java.util.UUID;

/** Projection returned by the staff room assignment JPA query. */
public record StaffRoomAssignment(UUID roomId, UUID accountId) { }
