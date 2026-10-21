package fu.fa26.sep490.g58.otcms_be.appointment;

import fu.fa26.sep490.g58.otcms_be.appointment.model.AppointmentOccupancy;
import fu.fa26.sep490.g58.otcms_be.appointment.model.AvailabilityInterval;
import fu.fa26.sep490.g58.otcms_be.appointment.model.AvailableSlot;
import fu.fa26.sep490.g58.otcms_be.appointment.model.CareOccupancy;
import fu.fa26.sep490.g58.otcms_be.appointment.model.DoctorSchedule;
import fu.fa26.sep490.g58.otcms_be.appointment.model.LocalDateWindow;
import fu.fa26.sep490.g58.otcms_be.appointment.service.impl.SlotAvailabilityEngine;
import fu.fa26.sep490.g58.otcms_be.appointment.exception.SlotAvailabilityException;
import fu.fa26.sep490.g58.otcms_be.appointment.model.SlotAvailabilityRequest;
import fu.fa26.sep490.g58.otcms_be.appointment.model.SlotServiceContext;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SlotAvailabilityEngineTest {
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final UUID DOCTOR_ID = UUID.randomUUID();
    private static final UUID SERVICE_ID = UUID.randomUUID();
    private final SlotAvailabilityEngine engine = new SlotAvailabilityEngine();

    @Test
    void generatesGridSlotsThatFitShiftAndAvoidTimeOffAppointmentsAndCare() {
        var context = SlotServiceContext.current(SERVICE_ID, Duration.ofMinutes(60), Duration.ofMinutes(15));
        var request = new SlotAvailabilityRequest(Set.of(DOCTOR_ID), LocalDate.of(2026, 10, 15),
                LocalDate.of(2026, 10, 15), context);
        var window = engine.resolveWindow(request, CLINIC_ZONE, 15, 31);
        var schedule = new DoctorSchedule(DOCTOR_ID,
                List.of(interval("2026-10-15T01:00:00Z", "2026-10-15T05:00:00Z")),
                List.of(interval("2026-10-15T02:15:00Z", "2026-10-15T02:30:00Z")));
        var appointment = new AppointmentOccupancy(UUID.randomUUID(), DOCTOR_ID,
                interval("2026-10-15T03:00:00Z", "2026-10-15T04:15:00Z"));
        var care = new CareOccupancy(UUID.randomUUID(), DOCTOR_ID, UUID.randomUUID(), null,
                interval("2026-10-15T04:15:00Z", "2026-10-15T04:45:00Z"));

        List<AvailableSlot> slots = engine.generate(request, window, CLINIC_ZONE, 15,
                List.of(schedule), List.of(appointment), List.of(care));

        assertEquals(List.of(Instant.parse("2026-10-15T01:00:00Z")),
                slots.stream().map(AvailableSlot::startTime).toList());
        assertEquals(Instant.parse("2026-10-15T02:15:00Z"), slots.getFirst().occupiedUntil());
    }

    @Test
    void snapshotContextExcludesOnlyItsOwnAppointmentAndValidLinkedCare() {
        UUID appointmentId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        var context = SlotServiceContext.snapshot(SERVICE_ID, 60, 15, appointmentId, patientId);
        var request = new SlotAvailabilityRequest(Set.of(DOCTOR_ID), LocalDate.of(2026, 10, 15),
                LocalDate.of(2026, 10, 15), context);
        var window = engine.resolveWindow(request, CLINIC_ZONE, 15, 31);
        var schedule = new DoctorSchedule(DOCTOR_ID,
                List.of(interval("2026-10-15T01:00:00Z", "2026-10-15T02:15:00Z")), List.of());
        var ownAppointment = new AppointmentOccupancy(appointmentId, DOCTOR_ID,
                interval("2026-10-15T01:00:00Z", "2026-10-15T02:15:00Z"));
        var ownCare = new CareOccupancy(UUID.randomUUID(), DOCTOR_ID, patientId, appointmentId,
                interval("2026-10-15T01:30:00Z", "2026-10-15T02:00:00Z"));

        assertEquals(1, engine.generate(request, window, CLINIC_ZONE, 15,
                List.of(schedule), List.of(ownAppointment), List.of(ownCare)).size());

        var competingAppointment = new AppointmentOccupancy(UUID.randomUUID(), DOCTOR_ID,
                interval("2026-10-15T01:00:00Z", "2026-10-15T02:15:00Z"));
        assertTrue(engine.generate(request, window, CLINIC_ZONE, 15,
                List.of(schedule), List.of(ownAppointment, competingAppointment), List.of(ownCare)).isEmpty());

        var otherPatientCare = new CareOccupancy(UUID.randomUUID(), DOCTOR_ID, UUID.randomUUID(), appointmentId,
                interval("2026-10-15T01:30:00Z", "2026-10-15T02:00:00Z"));
        assertTrue(engine.generate(request, window, CLINIC_ZONE, 15,
                List.of(schedule), List.of(ownAppointment), List.of(otherPatientCare)).isEmpty());
    }

    @Test
    void localDateWindowIsInclusiveAndUsesHalfOpenClinicZoneBounds() {
        var window = LocalDateWindow.resolve(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 16),
                CLINIC_ZONE, 31);

        assertEquals(Instant.parse("2026-10-14T17:00:00Z"), window.startInclusive());
        assertEquals(Instant.parse("2026-10-16T17:00:00Z"), window.endExclusive());
        assertThrows(SlotAvailabilityException.class, () -> LocalDateWindow.resolve(
                LocalDate.of(2026, 10, 15), LocalDate.of(2026, 11, 15), CLINIC_ZONE, 31));
        assertThrows(SlotAvailabilityException.class, () -> LocalDateWindow.resolve(
                LocalDate.of(2026, 10, 16), LocalDate.of(2026, 10, 15), CLINIC_ZONE, 31));
    }

    @Test
    void rejectsUnalignedStartWithoutRoundingAndHonorsEquivalentOffsets() {
        Instant aligned = Instant.parse("2026-10-15T01:00:00Z");
        assertDoesNotThrow(() -> engine.requireGridAligned(aligned, CLINIC_ZONE, 15));
        assertDoesNotThrow(() -> engine.requireGridAligned(
                Instant.parse("2026-10-15T08:00:00+07:00"), CLINIC_ZONE, 15));
        assertThrows(SlotAvailabilityException.class, () -> engine.requireGridAligned(
                Instant.parse("2026-10-15T01:01:00Z"), CLINIC_ZONE, 15));
        assertThrows(SlotAvailabilityException.class, () -> engine.requireGridAligned(
                Instant.parse("2026-10-15T01:00:01Z"), CLINIC_ZONE, 15));
        assertThrows(SlotAvailabilityException.class, () -> engine.requireGridAligned(
                Instant.parse("2026-10-15T01:00:00.000000001Z"), CLINIC_ZONE, 15));
    }

    @Test
    void serviceContextKeepsCurrentValuesSeparateFromAppointmentSnapshots() {
        var current = SlotServiceContext.current(SERVICE_ID, Duration.ofMinutes(45), Duration.ofMinutes(10));
        var snapshot = SlotServiceContext.snapshot(SERVICE_ID, 60, 15, UUID.randomUUID(), UUID.randomUUID());

        assertEquals(Duration.ofMinutes(55), current.occupiedDuration());
        assertEquals(Duration.ofMinutes(75), snapshot.occupiedDuration());
        assertEquals(SlotServiceContext.Source.CURRENT_SERVICE, current.source());
        assertEquals(SlotServiceContext.Source.APPOINTMENT_SNAPSHOT, snapshot.source());
    }

    @Test
    void localGridSkipsGapsAndIncludesBothOffsetsDuringDaylightSavingTransitions() {
        ZoneId newYork = ZoneId.of("America/New_York");
        var springDay = LocalDate.of(2026, 3, 8);
        var springWindow = LocalDateWindow.resolve(springDay, springDay, newYork, 1);
        assertEquals(Duration.ofHours(23), Duration.between(springWindow.startInclusive(), springWindow.endExclusive()));
        var springRequest = new SlotAvailabilityRequest(Set.of(DOCTOR_ID), springDay, springDay,
                SlotServiceContext.current(SERVICE_ID, Duration.ofMinutes(15), Duration.ZERO));
        var springSlots = engine.generate(springRequest, springWindow, newYork, 15,
                List.of(new DoctorSchedule(DOCTOR_ID, List.of(new AvailabilityInterval(
                        springWindow.startInclusive(), springWindow.endExclusive())), List.of())), List.of(), List.of());
        assertEquals(92, springSlots.size());

        var fallDay = LocalDate.of(2026, 11, 1);
        var fallWindow = LocalDateWindow.resolve(fallDay, fallDay, newYork, 1);
        assertEquals(Duration.ofHours(25), Duration.between(fallWindow.startInclusive(), fallWindow.endExclusive()));
        var fallRequest = new SlotAvailabilityRequest(Set.of(DOCTOR_ID), fallDay, fallDay,
                SlotServiceContext.current(SERVICE_ID, Duration.ofMinutes(15), Duration.ZERO));
        var fallSlots = engine.generate(fallRequest, fallWindow, newYork, 15,
                List.of(new DoctorSchedule(DOCTOR_ID, List.of(new AvailabilityInterval(
                        fallWindow.startInclusive(), fallWindow.endExclusive())), List.of())), List.of(), List.of());
        assertEquals(100, fallSlots.size());
    }

    @Test
    void invalidGridIntervalIsRejected() {
        var request = new SlotAvailabilityRequest(Set.of(DOCTOR_ID), LocalDate.of(2026, 10, 15),
                LocalDate.of(2026, 10, 15), SlotServiceContext.current(SERVICE_ID, Duration.ofMinutes(30), Duration.ZERO));
        assertThrows(SlotAvailabilityException.class, () -> engine.resolveWindow(request, CLINIC_ZONE, 17, 31));
    }

    private AvailabilityInterval interval(String start, String end) {
        return new AvailabilityInterval(Instant.parse(start), Instant.parse(end));
    }
}
