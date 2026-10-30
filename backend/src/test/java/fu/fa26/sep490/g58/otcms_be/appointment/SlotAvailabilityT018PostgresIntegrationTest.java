package fu.fa26.sep490.g58.otcms_be.appointment;

import fu.fa26.sep490.g58.otcms_be.appointment.service.GenerateAvailableSlotsService;
import fu.fa26.sep490.g58.otcms_be.appointment.model.SlotAvailabilityRequest;
import fu.fa26.sep490.g58.otcms_be.appointment.model.SlotServiceContext;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@LocalPostgresTest
class SlotAvailabilityT018PostgresIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired GenerateAvailableSlotsService generateSlots;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void combinesBoundedStaffAppointmentAndVisitIntervalsForCurrentAndSnapshotContexts() {
        UUID doctorAccountId = UUID.randomUUID();
        UUID receptionistAccountId = UUID.randomUUID();
        UUID doctorId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        UUID visitId = UUID.randomUUID();
        UUID careAssignmentId = UUID.randomUUID();
        UUID shiftId = UUID.randomUUID();
        UUID timeOffId = UUID.randomUUID();
        Instant dayStart = Instant.parse("2026-10-15T01:00:00Z");

        try {
            insertFixture(doctorAccountId, receptionistAccountId, doctorId, patientId, serviceId, appointmentId, visitId,
                    careAssignmentId, shiftId, timeOffId, dayStart);
            var currentContext = SlotServiceContext.current(serviceId, Duration.ofMinutes(60), Duration.ofMinutes(15));
            var currentRequest = new SlotAvailabilityRequest(Set.of(doctorId), LocalDate.of(2026, 10, 15),
                    LocalDate.of(2026, 10, 15), currentContext);

            var currentSlots = generateSlots.generate(currentRequest);
            assertEquals(Set.of(Instant.parse("2026-10-15T01:00:00Z")),
                    currentSlots.stream().map(slot -> slot.startTime()).collect(java.util.stream.Collectors.toSet()));

            var snapshotContext = SlotServiceContext.snapshot(serviceId, 60, 15, appointmentId, patientId);
            var snapshotRequest = new SlotAvailabilityRequest(Set.of(doctorId), LocalDate.of(2026, 10, 15),
                    LocalDate.of(2026, 10, 15), snapshotContext);
            var snapshotSlots = generateSlots.generate(snapshotRequest);

            assertTrue(snapshotSlots.stream().anyMatch(slot -> slot.startTime().equals(Instant.parse("2026-10-15T02:30:00Z"))));
            assertTrue(snapshotSlots.stream().anyMatch(slot -> slot.startTime().equals(Instant.parse("2026-10-15T03:45:00Z"))));
            assertTrue(snapshotSlots.stream().allMatch(slot -> slot.doctorProfileId().equals(doctorId)
                    && slot.serviceId().equals(serviceId)));
        } finally {
            deleteFixture(doctorAccountId, receptionistAccountId, doctorId, patientId, serviceId, appointmentId, visitId,
                    careAssignmentId, shiftId, timeOffId);
        }
    }

    private void insertFixture(UUID doctorAccountId, UUID receptionistAccountId, UUID doctorId, UUID patientId, UUID serviceId,
                               UUID appointmentId, UUID visitId, UUID careAssignmentId,
                               UUID shiftId, UUID timeOffId, Instant dayStart) {
        Instant createdAt = Instant.parse("2026-10-01T00:00:00Z");
        jdbc.update("insert into accounts (id, normalized_email, account_type, status, created_at, updated_at) "
                        + "values (?, ?, 'DOCTOR', 'ACTIVE', ?, ?)", doctorAccountId,
                "t018-" + doctorAccountId + "@example.test", Timestamp.from(createdAt), Timestamp.from(createdAt));
        jdbc.update("insert into accounts (id, normalized_email, account_type, status, created_at, updated_at) "
                        + "values (?, ?, 'RECEPTIONIST', 'ACTIVE', ?, ?)", receptionistAccountId,
                "t018-receptionist-" + receptionistAccountId + "@example.test", Timestamp.from(createdAt), Timestamp.from(createdAt));
        jdbc.update("insert into patient_profiles (id, account_id, full_name, phone_canonical, phone_owner_type, date_of_birth, gender) "
                        + "values (?, null, 'T018 patient', '+84911111111', 'PATIENT', ?, 'OTHER')",
                patientId, Date.valueOf("1990-01-01"));
        jdbc.update("insert into clinic_services (id, name, description, duration_minutes, buffer_minutes, online_bookable, active) "
                        + "values (?, 'T018 service', '', 60, 15, true, true)", serviceId);
        jdbc.update("insert into doctor_profiles (id, account_id, full_name, specialties, qualifications, biography, consultation_information) "
                        + "values (?, ?, 'T018 Doctor', '', '', '', '')", doctorId, doctorAccountId);
        jdbc.update("insert into doctor_services (doctor_profile_id, service_id, active) values (?, ?, true)",
                doctorId, serviceId);
        jdbc.update("insert into doctor_shifts (id, doctor_profile_id, starts_at, ends_at) values (?, ?, ?, ?)",
                shiftId, doctorId, Timestamp.from(dayStart), Timestamp.from(dayStart.plus(Duration.ofHours(4))));
        jdbc.update("insert into doctor_time_off (id, doctor_profile_id, starts_at, ends_at) values (?, ?, ?, ?)",
                timeOffId, doctorId, Timestamp.from(dayStart.plus(Duration.ofMinutes(75))),
                Timestamp.from(dayStart.plus(Duration.ofMinutes(90))));
        jdbc.update("insert into appointments (id, patient_profile_id, service_id, doctor_profile_id, status, booking_source, purpose, "
                        + "start_time, end_time, duration_minutes, buffer_minutes, created_by_account_id) "
                        + "values (?, ?, ?, ?, 'CONFIRMED', 'PATIENT_ONLINE', 'T018 test', ?, ?, 60, 15, ?)",
                appointmentId, patientId, serviceId, doctorId, Timestamp.from(dayStart.plus(Duration.ofMinutes(150))),
                Timestamp.from(dayStart.plus(Duration.ofMinutes(210))), doctorAccountId);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.update("insert into visits (id, patient_profile_id, source, originating_appointment_id, arrived_at, checked_in_at, created_by_account_id) "
                            + "values (?, ?, 'SCHEDULED', ?, ?, ?, ?)", visitId, patientId, appointmentId,
                    Timestamp.from(dayStart), Timestamp.from(dayStart), receptionistAccountId);
            jdbc.update("insert into visit_check_ins (visit_id, receptionist_account_id, reception_type, arrived_at, checked_in_at) "
                            + "values (?, ?, 'APPOINTMENT_CHECK_IN', ?, ?)", visitId, receptionistAccountId,
                    Timestamp.from(dayStart), Timestamp.from(dayStart));
            jdbc.update("update appointments set status = 'CHECKED_IN', visit_id = ? where id = ?", visitId, appointmentId);
        });
        jdbc.update("insert into visit_assignments (id, visit_id, doctor_profile_id, starts_at, ends_at, status) "
                        + "values (?, ?, ?, ?, ?, 'RESERVED')", careAssignmentId, visitId, doctorId,
                Timestamp.from(dayStart.plus(Duration.ofMinutes(195))),
                Timestamp.from(dayStart.plus(Duration.ofMinutes(225))));
    }

    private void deleteFixture(UUID doctorAccountId, UUID receptionistAccountId, UUID doctorId, UUID patientId, UUID serviceId,
                               UUID appointmentId, UUID visitId, UUID careAssignmentId,
                               UUID shiftId, UUID timeOffId) {
        jdbc.update("update appointments set status = 'CONFIRMED', visit_id = null where id = ?", appointmentId);
        jdbc.update("delete from visit_assignments where id = ?", careAssignmentId);
        jdbc.update("delete from visit_check_ins where visit_id = ?", visitId);
        jdbc.update("delete from visits where id = ?", visitId);
        jdbc.update("delete from appointments where id = ?", appointmentId);
        jdbc.update("delete from doctor_time_off where id = ?", timeOffId);
        jdbc.update("delete from doctor_shifts where id = ?", shiftId);
        jdbc.update("delete from doctor_services where doctor_profile_id = ? and service_id = ?", doctorId, serviceId);
        jdbc.update("delete from clinic_services where id = ?", serviceId);
        jdbc.update("delete from patient_profiles where id = ?", patientId);
        jdbc.update("delete from doctor_profiles where id = ?", doctorId);
        jdbc.update("delete from accounts where id = ?", receptionistAccountId);
        jdbc.update("delete from accounts where id = ?", doctorAccountId);
    }
}
