package fu.fa26.sep490.g58.otcms_be.appointment.service.impl;
import fu.fa26.sep490.g58.otcms_be.appointment.model.AppointmentOccupancy;
import fu.fa26.sep490.g58.otcms_be.appointment.model.AvailabilityInterval;
import fu.fa26.sep490.g58.otcms_be.appointment.model.AvailableSlot;
import fu.fa26.sep490.g58.otcms_be.appointment.model.CareOccupancy;
import fu.fa26.sep490.g58.otcms_be.appointment.model.DoctorSchedule;
import fu.fa26.sep490.g58.otcms_be.appointment.model.LocalDateWindow;
import fu.fa26.sep490.g58.otcms_be.appointment.model.SlotAvailabilityRequest;

import fu.fa26.sep490.g58.otcms_be.appointment.exception.SlotAvailabilityException;
import fu.fa26.sep490.g58.otcms_be.appointment.model.SlotServiceContext;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Pure slot generation over bounded owner-provided interval projections. */
public final class SlotAvailabilityEngine {
    public LocalDateWindow resolveWindow(SlotAvailabilityRequest request, ZoneId clinicZone,
                                         int slotIntervalMinutes, long maxInclusiveDates) {
        validateGrid(slotIntervalMinutes);
        return LocalDateWindow.resolve(request.fromDate(), request.toDate(), clinicZone, maxInclusiveDates);
    }

    public List<AvailableSlot> generate(SlotAvailabilityRequest request, LocalDateWindow window,
                                       ZoneId clinicZone, int slotIntervalMinutes,
                                       Collection<DoctorSchedule> schedules,
                                       Collection<AppointmentOccupancy> appointments,
                                       Collection<CareOccupancy> careReservations) {
        validateGrid(slotIntervalMinutes);
        if (window.isEmpty() || request.doctorProfileIds().isEmpty()) return List.of();
        Map<UUID, DoctorSchedule> scheduleByDoctor = schedules.stream()
                .collect(Collectors.toMap(DoctorSchedule::doctorProfileId, Function.identity(), (left, right) -> left));
        Map<UUID, List<AppointmentOccupancy>> appointmentsByDoctor = appointments.stream()
                .collect(Collectors.groupingBy(AppointmentOccupancy::doctorProfileId));
        Map<UUID, List<CareOccupancy>> careByDoctor = careReservations.stream()
                .collect(Collectors.groupingBy(CareOccupancy::doctorProfileId));

        List<AvailableSlot> slots = new ArrayList<>();
        Set<UUID> doctorIds = new TreeSet<>(request.doctorProfileIds());
        for (UUID doctorId : doctorIds) {
            DoctorSchedule schedule = scheduleByDoctor.get(doctorId);
            if (schedule == null || schedule.shifts().isEmpty()) continue;
            List<AppointmentOccupancy> doctorAppointments = appointmentsByDoctor.getOrDefault(doctorId, List.of());
            List<CareOccupancy> doctorCare = careByDoctor.getOrDefault(doctorId, List.of());
            for (LocalDate date = window.fromInclusive(); !date.isAfter(window.toInclusive()); date = date.plusDays(1)) {
                for (int minuteOfDay = 0; minuteOfDay < 1440; minuteOfDay += slotIntervalMinutes) {
                    LocalDateTime localStart = date.atTime(LocalTime.of(minuteOfDay / 60, minuteOfDay % 60));
                    for (ZoneOffset offset : clinicZone.getRules().getValidOffsets(localStart)) {
                        Instant start = localStart.atOffset(offset).toInstant();
                        Instant consultationEnd = start.plus(request.serviceContext().consultationDuration());
                        Instant occupiedUntil = consultationEnd.plus(request.serviceContext().bufferDuration());
                        AvailabilityInterval occupiedInterval = new AvailabilityInterval(start, occupiedUntil);
                        if (!fitsShift(occupiedInterval, schedule.shifts())
                                || intersects(occupiedInterval, schedule.timeOff())
                                || conflictsAppointment(request.serviceContext(), doctorAppointments, occupiedInterval)
                                || conflictsCare(request.serviceContext(), doctorCare, occupiedInterval)) {
                            continue;
                        }
                        slots.add(new AvailableSlot(request.serviceContext().serviceId(), doctorId,
                                start, consultationEnd, occupiedUntil));
                    }
                }
            }
        }
        slots.sort(Comparator.comparing(AvailableSlot::startTime).thenComparing(AvailableSlot::doctorProfileId));
        return List.copyOf(slots);
    }

    public void requireGridAligned(Instant startTime, ZoneId clinicZone, int slotIntervalMinutes) {
        validateGrid(slotIntervalMinutes);
        if (startTime == null || clinicZone == null) throw new SlotAvailabilityException("VALIDATION_FAILED");
        LocalDateTime localStart = startTime.atZone(clinicZone).toLocalDateTime();
        if (localStart.getSecond() != 0 || localStart.getNano() != 0
                || localStart.getMinute() % slotIntervalMinutes != 0) {
            throw new SlotAvailabilityException("VALIDATION_FAILED");
        }
    }

    private boolean fitsShift(AvailabilityInterval slot, List<AvailabilityInterval> shifts) {
        return shifts.stream().anyMatch(shift -> shift.contains(slot));
    }

    private boolean intersects(AvailabilityInterval slot, List<AvailabilityInterval> intervals) {
        return intervals.stream().anyMatch(slot::overlaps);
    }

    private boolean conflictsAppointment(SlotServiceContext context, List<AppointmentOccupancy> appointments,
                                         AvailabilityInterval slot) {
        return appointments.stream().anyMatch(appointment ->
                !appointment.appointmentId().equals(context.excludedAppointmentId())
                        && appointment.interval().overlaps(slot));
    }

    private boolean conflictsCare(SlotServiceContext context, List<CareOccupancy> careReservations,
                                  AvailabilityInterval slot) {
        return careReservations.stream().anyMatch(care -> !isOwnLinkedCare(context, care)
                && care.interval().overlaps(slot));
    }

    private boolean isOwnLinkedCare(SlotServiceContext context, CareOccupancy care) {
        return context.source() == SlotServiceContext.Source.APPOINTMENT_SNAPSHOT
                && context.excludedAppointmentId().equals(care.linkedAppointmentId())
                && context.patientProfileId().equals(care.patientProfileId());
    }

    private void validateGrid(int slotIntervalMinutes) {
        if (slotIntervalMinutes < 1 || 1440 % slotIntervalMinutes != 0) {
            throw new SlotAvailabilityException("VALIDATION_FAILED");
        }
    }
}
