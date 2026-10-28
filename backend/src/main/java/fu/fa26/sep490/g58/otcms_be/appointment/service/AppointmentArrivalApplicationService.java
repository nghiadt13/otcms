package fu.fa26.sep490.g58.otcms_be.appointment.service;

import fu.fa26.sep490.g58.otcms_be.appointment.model.dto.response.ArrivalResponse;
import fu.fa26.sep490.g58.otcms_be.appointment.model.dto.response.ArrivalWorklistResponse;
import java.time.LocalDate;
import java.util.UUID;

public interface AppointmentArrivalApplicationService {
    ArrivalResponse recordQr(UUID appointmentId, UUID accountId, String proof);
    ArrivalResponse recordManual(UUID appointmentId, UUID accountId, String reason);
    ArrivalWorklistResponse list(LocalDate clinicDate, String state, int page, int size);
}
