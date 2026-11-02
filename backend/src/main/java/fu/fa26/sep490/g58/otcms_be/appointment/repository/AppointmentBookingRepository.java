package fu.fa26.sep490.g58.otcms_be.appointment.repository;
import fu.fa26.sep490.g58.otcms_be.appointment.model.CreatedAppointment;


import fu.fa26.sep490.g58.otcms_be.appointment.model.AppointmentBooking;

public interface AppointmentBookingRepository {
    CreatedAppointment save(AppointmentBooking booking);
    CreatedAppointment saveFollowUp(AppointmentBooking booking);
}
