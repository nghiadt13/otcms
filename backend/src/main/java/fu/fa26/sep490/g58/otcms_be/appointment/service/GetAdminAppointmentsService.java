package fu.fa26.sep490.g58.otcms_be.appointment.service;

public interface GetAdminAppointmentsService {
    AdminAppointmentSearchPage list(AppointmentSearchFilter filter, int page, int size);
}
