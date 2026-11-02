package fu.fa26.sep490.g58.otcms_be.appointment.service;

import fu.fa26.sep490.g58.otcms_be.appointment.model.dto.response.ArrivalResponse;
import fu.fa26.sep490.g58.otcms_be.appointment.model.dto.response.ArrivalWorklistResponse;
import fu.fa26.sep490.g58.otcms_be.appointment.model.dto.response.PatientQrCheckInResponse;
import java.time.LocalDate;
import java.util.UUID;

public interface AppointmentArrivalApplicationService {
    PatientQrCheckInResponse recordQr(UUID appointmentId, UUID accountId, String proof, String correlationId);
    ArrivalResponse recordManual(UUID appointmentId, UUID accountId, String reason);
    ArrivalWorklistResponse list(LocalDate clinicDate, String state, int page, int size);
}
