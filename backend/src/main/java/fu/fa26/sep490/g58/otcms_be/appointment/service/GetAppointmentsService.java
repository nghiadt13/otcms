package fu.fa26.sep490.g58.otcms_be.appointment.service;

import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;

import java.util.UUID;

public interface GetAppointmentsService {
    AppointmentSearchPage list(UUID accountId, AccountRole role, AppointmentSearchFilter filter, int page, int size);

    AppointmentSummary detail(UUID accountId, AccountRole role, UUID appointmentId);
}
