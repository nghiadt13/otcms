package fu.fa26.sep490.g58.otcms_be.appointment.service.impl;
import fu.fa26.sep490.g58.otcms_be.appointment.repository.AppointmentOccupancyRepository;
import fu.fa26.sep490.g58.otcms_be.appointment.service.GenerateAvailableSlotsService;

import fu.fa26.sep490.g58.otcms_be.appointment.model.AvailableSlot;
import fu.fa26.sep490.g58.otcms_be.appointment.model.AvailabilityInterval;
import fu.fa26.sep490.g58.otcms_be.appointment.model.CareOccupancy;
import fu.fa26.sep490.g58.otcms_be.appointment.model.DoctorSchedule;
import fu.fa26.sep490.g58.otcms_be.appointment.model.LocalDateWindow;
import fu.fa26.sep490.g58.otcms_be.appointment.service.impl.SlotAvailabilityEngine;
import fu.fa26.sep490.g58.otcms_be.appointment.model.SlotAvailabilityRequest;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffScheduleAvailabilityService;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitCareReservationQueryService;
import fu.fa26.sep490.g58.otcms_be.config.AppointmentBookingProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class GenerateAvailableSlotsServiceImpl implements GenerateAvailableSlotsService {
    private final AppointmentBookingProperties properties;
    private final StaffScheduleAvailabilityService staffSchedules;
    private final AppointmentOccupancyRepository appointments;
    private final VisitCareReservationQueryService careReservations;
    private final SlotAvailabilityEngine engine;

    @Autowired
    public GenerateAvailableSlotsServiceImpl(AppointmentBookingProperties properties,
                                         StaffScheduleAvailabilityService staffSchedules,
                                         AppointmentOccupancyRepository appointments,
                                         VisitCareReservationQueryService careReservations) {
        this(properties, staffSchedules, appointments, careReservations, new SlotAvailabilityEngine());
    }

    GenerateAvailableSlotsServiceImpl(AppointmentBookingProperties properties,
                                  StaffScheduleAvailabilityService staffSchedules,
                                  AppointmentOccupancyRepository appointments,
                                  VisitCareReservationQueryService careReservations,
                                  SlotAvailabilityEngine engine) {
        this.properties = properties;
        this.staffSchedules = staffSchedules;
        this.appointments = appointments;
        this.careReservations = careReservations;
        this.engine = engine;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AvailableSlot> generate(SlotAvailabilityRequest request) {
        LocalDateWindow window = engine.resolveWindow(request, properties.clinicTimeZone(),
                properties.slotIntervalMinutes(), (long) properties.maxHorizonDays() + 1);
        Set<java.util.UUID> doctorIds = request.doctorProfileIds();
        if (window.isEmpty() || doctorIds.isEmpty()) return List.of();

        List<DoctorSchedule> schedules = staffSchedules.findSchedules(doctorIds,
                        window.startInclusive(), window.endExclusive()).stream()
                .map(schedule -> new DoctorSchedule(schedule.doctorProfileId(),
                        schedule.shifts().stream().map(interval -> new AvailabilityInterval(
                                interval.startsAt(), interval.endsAt())).toList(),
                        schedule.timeOff().stream().map(interval -> new AvailabilityInterval(
                                interval.startsAt(), interval.endsAt())).toList()))
                .toList();
        var occupiedAppointments = appointments.findOccupiedIntervals(doctorIds,
                window.startInclusive(), window.endExclusive(), request.serviceContext().excludedAppointmentId());
        List<CareOccupancy> occupiedCare = careReservations.findActiveCareReservations(doctorIds,
                        window.startInclusive(), window.endExclusive()).stream()
                .map(care -> new CareOccupancy(care.assignmentId(), care.doctorProfileId(), care.patientProfileId(),
                        care.linkedAppointmentId(), new AvailabilityInterval(care.startsAt(), care.endsAt())))
                .toList();
        return engine.generate(request, window, properties.clinicTimeZone(), properties.slotIntervalMinutes(),
                schedules, occupiedAppointments, occupiedCare);
    }
}
