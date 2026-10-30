package fu.fa26.sep490.g58.otcms_be.appointment.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface GetAppointmentChangeAvailabilityService {
    List<AppointmentAvailabilitySlot> findRescheduleSlots(UUID appointmentId, UUID accountId,
                                                          AppointmentAvailabilityQuery.Audience actor,
                                                          LocalDate fromDate, LocalDate toDate);

    List<AppointmentAvailabilitySlot> findReassignmentSlots(UUID appointmentId, LocalDate fromDate,
                                                            LocalDate toDate);
}
