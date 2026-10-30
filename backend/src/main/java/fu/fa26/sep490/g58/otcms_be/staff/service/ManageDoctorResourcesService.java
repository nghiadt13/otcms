package fu.fa26.sep490.g58.otcms_be.staff.service;

import fu.fa26.sep490.g58.otcms_be.staff.model.dto.response.DoctorIntervalResponse;
import fu.fa26.sep490.g58.otcms_be.staff.model.DoctorProfile;
import fu.fa26.sep490.g58.otcms_be.common.model.AdminMutationImpact;
import java.time.Instant;
import java.util.UUID;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;

public interface ManageDoctorResourcesService {
    DoctorIntervalResponse createShift(UUID doctorId, Instant startsAt, Instant endsAt, AuthenticatedAccount actor, String correlationId);
    void deleteShift(UUID doctorId, UUID shiftId, AuthenticatedAccount actor, String correlationId);
    DoctorIntervalResponse updateShift(UUID doctorId, UUID shiftId, Instant startsAt, Instant endsAt, AuthenticatedAccount actor, String correlationId);
    DoctorIntervalResponse createTimeOff(UUID doctorId, Instant startsAt, Instant endsAt, AuthenticatedAccount actor, String correlationId);
    void deleteTimeOff(UUID doctorId, UUID timeOffId, AuthenticatedAccount actor, String correlationId);
    DoctorIntervalResponse updateTimeOff(UUID doctorId, UUID timeOffId, Instant startsAt, Instant endsAt, AuthenticatedAccount actor, String correlationId);
    DoctorProfile updateDirectoryState(UUID doctorId, boolean publiclyVisible, boolean acceptingNewBookings,
            boolean inActiveDirectory, AuthenticatedAccount actor, String correlationId);
    AdminMutationImpact assessImpact(UUID doctorId, String resourceType, UUID resourceId,
            Instant startsAt, Instant endsAt, AuthenticatedAccount actor);
    AdminMutationImpact assessTimeOffRemovalImpact(UUID doctorId, UUID timeOffId, AuthenticatedAccount actor);
}
