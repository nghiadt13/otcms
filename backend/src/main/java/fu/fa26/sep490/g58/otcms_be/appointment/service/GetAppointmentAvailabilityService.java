package fu.fa26.sep490.g58.otcms_be.appointment.service;

import java.util.List;

public interface GetAppointmentAvailabilityService {
    List<AppointmentAvailabilitySlot> find(AppointmentAvailabilityQuery query);
}
