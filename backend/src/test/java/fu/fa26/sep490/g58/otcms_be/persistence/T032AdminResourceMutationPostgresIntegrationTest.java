package fu.fa26.sep490.g58.otcms_be.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.ManageDoctorServiceAssignmentsService;
import fu.fa26.sep490.g58.otcms_be.staff.service.ManageDoctorResourcesService;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import java.time.Duration;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@LocalPostgresTest
class T032AdminResourceMutationPostgresIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-20T09:00:00Z");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired CoordinationGuardService guard;
    @Autowired ManageDoctorResourcesService staffResources;
    @Autowired ManageDoctorServiceAssignmentsService assignments;

    private UUID adminId;
    private UUID doctorAccountId;
    private UUID receptionistId;
    private UUID doctorId;
    private UUID serviceId;
    private UUID assignmentId;
    private UUID shiftId;
    private UUID timeOffId;
    private final List<UUID> patientIds = new java.util.ArrayList<>();
    private final List<UUID> patientAccountIds = new java.util.ArrayList<>();
    private final List<UUID> appointmentIds = new java.util.ArrayList<>();
    private final List<UUID> visitIds = new java.util.ArrayList<>();

    @AfterEach
    void cleanup() {
        jdbc.execute("alter table staff_resource_audits disable trigger tr_staff_resource_audits_immutable");
        jdbc.execute("alter table service_audits disable trigger tr_service_catalog_audits_immutable");
        try {
            for (UUID appointment : appointmentIds) {
                jdbc.update("update appointments set status='CANCELLED', visit_id=null where id=?", appointment);
            }
            for (UUID visit : visitIds) {
                jdbc.update("delete from visit_check_ins where visit_id=?", visit);
                jdbc.update("delete from visit_assignments where visit_id=?", visit);
            }
            for (UUID visit : visitIds) jdbc.update("delete from visits where id=?", visit);
            for (UUID appointment : appointmentIds) jdbc.update("delete from appointments where id=?", appointment);
            if (doctorId != null) {
                jdbc.update("delete from staff_resource_audits where doctor_profile_id = ?", doctorId);
                if (serviceId != null) jdbc.update("delete from service_audits where service_id = ?", serviceId);
                jdbc.update("delete from doctor_time_off where doctor_profile_id = ?", doctorId);
                jdbc.update("delete from doctor_shifts where doctor_profile_id = ?", doctorId);
                jdbc.update("delete from doctor_services where doctor_profile_id = ?", doctorId);
                jdbc.update("delete from doctor_profiles where id = ?", doctorId);
            }
            if (serviceId != null) jdbc.update("delete from clinic_services where id = ?", serviceId);
            for (UUID patient : patientIds) jdbc.update("delete from patient_profiles where id=?", patient);
            if (adminId != null) jdbc.update("delete from accounts where id = ?", adminId);
            if (doctorAccountId != null) jdbc.update("delete from accounts where id = ?", doctorAccountId);
            if (receptionistId != null) jdbc.update("delete from accounts where id = ?", receptionistId);
            for (UUID account : patientAccountIds) jdbc.update("delete from accounts where id=?", account);
            patientIds.clear(); patientAccountIds.clear(); appointmentIds.clear(); visitIds.clear();
        } finally {
            jdbc.execute("alter table staff_resource_audits enable trigger tr_staff_resource_audits_immutable");
            jdbc.execute("alter table service_audits enable trigger tr_service_catalog_audits_immutable");
        }
    }

    @Test
    void migratesV019FromCleanAndV018WhilePreservingExistingRows() {
        String clean = createSchema("t032_clean_");
        try {
            Flyway flyway = flyway(clean, "19");
            assertEquals(21, flyway.migrate().migrationsExecuted);
            assertEquals("019", flyway.info().current().getVersion().getVersion());
            assertEquals(1, tableCount(clean, "staff_resource_management_audits"));
            assertEquals(1, tableCount(clean, "service_catalog_management_audits"));
            assertEquals(2, triggerCount(clean, "tr_staff_resource_audits_immutable"));
            assertEquals(2, triggerCount(clean, "tr_service_catalog_audits_immutable"));
        } finally {
            dropSchema(clean);
        }

        String upgrade = createSchema("t032_v018_");
        try {
            Flyway before = flyway(upgrade, "18");
            assertEquals(20, before.migrate().migrationsExecuted);
            UUID accountId = UUID.randomUUID();
            UUID service = UUID.randomUUID();
            UUID doctor = UUID.randomUUID();
            jdbc.update("insert into " + upgrade + ".accounts (id,normalized_email,account_type,status,created_at,updated_at) values (?,?,?, 'ACTIVE', now(), now())",
                    accountId, "t032-upgrade-" + accountId + "@example.test", "ADMIN");
            jdbc.update("insert into " + upgrade + ".clinic_services (id,name,duration_minutes,buffer_minutes,online_bookable,active) values (?,?,30,5,true,true)",
                    service, "T032 preserve service");
            Integer v018Checksum = jdbc.queryForObject("select checksum from " + upgrade + ".flyway_schema_history where version='018'", Integer.class);
            Flyway after = flyway(upgrade, "19");
            assertEquals(1, after.migrate().migrationsExecuted);
            assertEquals(v018Checksum, jdbc.queryForObject("select checksum from " + upgrade + ".flyway_schema_history where version='018'", Integer.class));
            assertEquals(1, jdbc.queryForObject("select count(*) from " + upgrade + ".accounts where id=?", Integer.class, accountId));
            assertEquals(1, jdbc.queryForObject("select count(*) from " + upgrade + ".clinic_services where id=? and name='T032 preserve service'", Integer.class, service));
            assertEquals(1, tableCount(upgrade, "staff_resource_management_audits"));
            assertEquals(1, tableCount(upgrade, "service_catalog_management_audits"));
        } finally {
            dropSchema(upgrade);
        }
    }

    @Test
    void adminMutationsReturnImpactConflictsAndWriteOnlySuccessfulOwnerAudits() throws Exception {
        createFixtures();
        jdbc.update("insert into doctor_shifts (id,doctor_profile_id,starts_at,ends_at) values (?,?,?,?)",
                shiftId, doctorId, timestamp(NOW), timestamp(NOW.plus(Duration.ofHours(2))));
        UUID bookingId = UUID.randomUUID(); appointmentIds.add(bookingId);
        jdbc.update("insert into appointments (id,patient_profile_id,service_id,doctor_profile_id,status,booking_source,purpose,start_time,end_time,duration_minutes,buffer_minutes,created_by_account_id) " +
                        "values (?, ?, ?, ?, 'CONFIRMED', 'RECEPTIONIST_ASSISTED', 't032 fixture', ?, ?, 30, 10, ?)",
                bookingId, patientId(), serviceId, doctorId, timestamp(NOW.plus(Duration.ofMinutes(30))),
                timestamp(NOW.plus(Duration.ofMinutes(60))), adminId);

        mvc.perform(delete("/api/v1/admin/doctors/{doctorId}/shifts/{shiftId}", doctorId, shiftId)
                        .with(actor(AccountRole.ADMIN, adminId)).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_MUTATION_BLOCKED"))
                .andExpect(jsonPath("$.error.impact.resourceType").value("WORK_SHIFT"))
                .andExpect(jsonPath("$.error.impact.resourceId").value(shiftId.toString()))
                .andExpect(jsonPath("$.error.impact.affectedBookingCount").value(1))
                .andExpect(jsonPath("$.error.impact.activeCareBlocks").value(false))
                .andExpect(jsonPath("$.error.impact.patientId").doesNotExist());
        assertEquals(1, jdbc.queryForObject("select count(*) from doctor_shifts where id=?", Integer.class, shiftId));
        assertEquals(0, jdbc.queryForObject("select count(*) from staff_resource_audits where resource_id=?", Integer.class, shiftId));

        mvc.perform(post("/api/v1/admin/doctors/{doctorId}/time-offs", doctorId)
                        .with(actor(AccountRole.ADMIN, adminId)).with(csrf()).contentType("application/json")
                        .content("{\"startsAt\":\"2026-10-20T09:20:00Z\",\"endsAt\":\"2026-10-20T10:10:00Z\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_MUTATION_BLOCKED"))
                .andExpect(jsonPath("$.error.impact.resourceType").value("TIME_OFF"))
                .andExpect(jsonPath("$.error.impact.affectedBookingCount").value(1));
        assertEquals(0, jdbc.queryForObject("select count(*) from doctor_time_off where doctor_profile_id=?", Integer.class, doctorId));
        assertEquals(0, jdbc.queryForObject("select count(*) from staff_resource_audits where doctor_profile_id=? and resource_type='TIME_OFF'", Integer.class, doctorId));

        timeOffId = UUID.randomUUID();
        jdbc.update("insert into doctor_time_off (id,doctor_profile_id,starts_at,ends_at) values (?,?,?,?)",
                timeOffId, doctorId, timestamp(NOW.plus(Duration.ofMinutes(20))), timestamp(NOW.plus(Duration.ofMinutes(70))));
        mvc.perform(get("/api/v1/admin/doctors/{doctorId}/time-offs/{timeOffId}/mutation-impact", doctorId, timeOffId)
                        .with(actor(AccountRole.ADMIN, adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocked").value(false))
                .andExpect(jsonPath("$.affectedBookingCount").value(0))
                .andExpect(jsonPath("$.activeCareBlocks").value(false));
        mvc.perform(delete("/api/v1/admin/doctors/{doctorId}/time-offs/{timeOffId}", doctorId, timeOffId)
                        .with(actor(AccountRole.ADMIN, adminId)).with(csrf()))
                .andExpect(status().isNoContent());
        assertEquals(0, jdbc.queryForObject("select count(*) from doctor_time_off where id=?", Integer.class, timeOffId));
        assertEquals(1, jdbc.queryForObject("select count(*) from staff_resource_audits where resource_id=? and resource_type='TIME_OFF' and action='DELETED'", Integer.class, timeOffId));

        mvc.perform(post("/api/v1/admin/doctors/{doctorId}/shifts", doctorId).with(actor(AccountRole.ADMIN, adminId)).with(csrf())
                        .contentType("application/json").content("{\"startsAt\":\"2026-10-20T12:00:00Z\",\"endsAt\":\"2026-10-20T13:00:00Z\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").exists());
        assertEquals(1, jdbc.queryForObject("select count(*) from staff_resource_audits where doctor_profile_id=? and resource_type='WORK_SHIFT' and action='CREATED'", Integer.class, doctorId));
        mvc.perform(patch("/api/v1/admin/doctors/{doctorId}/directory-state", doctorId)
                        .with(actor(AccountRole.ADMIN, adminId)).with(csrf()).contentType("application/json")
                        .content("{\"publiclyVisible\":false,\"acceptingNewBookings\":false,\"inActiveDirectory\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.publiclyVisible").value(false));
        assertEquals(1, jdbc.queryForObject("select count(*) from staff_resource_audits where doctor_profile_id=? and resource_type='DOCTOR_PROFILE' and action='UPDATED'", Integer.class, doctorId));
        UUID auditId = jdbc.queryForObject("select id from staff_resource_audits where doctor_profile_id=? and resource_type='WORK_SHIFT' and action='CREATED'", UUID.class, doctorId);
        assertThrows(DataAccessException.class, () -> jdbc.update("update staff_resource_audits set correlation_id='tampered' where id=?", auditId));

        mvc.perform(delete("/api/v1/admin/clinic-services/{serviceId}", serviceId)
                        .with(actor(AccountRole.ADMIN, adminId)).with(csrf()))
                .andExpect(status().isNoContent());
        assertEquals(1, jdbc.queryForObject("select count(*) from appointments where service_id=? and status='CONFIRMED' and duration_minutes=30 and buffer_minutes=10", Integer.class, serviceId));
        assertEquals(1, jdbc.queryForObject("select count(*) from clinic_services where id=? and active=false", Integer.class, serviceId));
        assertEquals(1, jdbc.queryForObject("select count(*) from service_audits where service_id=? and action='DEACTIVATED'", Integer.class, serviceId));

        mvc.perform(post("/api/v1/admin/clinic-services/{serviceId}/doctor-assignments", serviceId)
                        .with(actor(AccountRole.RECEPTIONIST, receptionistId)).with(csrf()).contentType("application/json")
                        .content("{\"doctorProfileId\":\"" + doctorId + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void assignmentRemovalIsBlockedByActiveCareAcrossAllServices() throws Exception {
        createFixtures();
        UUID visitId = UUID.randomUUID();
        visitIds.add(visitId);
        UUID patientId = patientId();
        Instant careStart = NOW.plus(Duration.ofDays(1));
        Instant careEnd = careStart.plus(Duration.ofMinutes(30));
        jdbc.update("insert into visits (id,patient_profile_id,source,arrived_at,checked_in_at,created_by_account_id,status) values (?,?,'WALK_IN',?, ?, ?, 'OPEN')",
                visitId, patientId, timestamp(NOW), timestamp(NOW), adminId);
        jdbc.update("insert into visit_assignments (id,visit_id,doctor_profile_id,starts_at,ends_at,status) values (?,?,?, ?, ?, 'RESERVED')",
                UUID.randomUUID(), visitId, doctorId, timestamp(careStart), timestamp(careEnd));

        mvc.perform(delete("/api/v1/admin/clinic-services/{serviceId}/doctor-assignments/{assignmentId}", serviceId, assignmentId)
                        .with(actor(AccountRole.ADMIN, adminId)).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_MUTATION_BLOCKED"))
                .andExpect(jsonPath("$.error.impact.resourceType").value("DOCTOR_ASSIGNMENT"))
                .andExpect(jsonPath("$.error.impact.activeCareBlocks").value(true));
        assertEquals(1, jdbc.queryForObject("select count(*) from doctor_services where id=? and active", Integer.class, assignmentId));
        assertEquals(0, jdbc.queryForObject("select count(*) from service_audits where resource_id=? and action='DELETED'", Integer.class, assignmentId));
    }

    @Test
    void checkedInAppointmentAlsoBlocksTimeOffCreation() throws Exception {
        createFixtures();
        UUID patient = patientId();
        UUID appointmentId = newBookingId();
        UUID visitId = UUID.randomUUID();
        visitIds.add(visitId);
        Instant appointmentStart = NOW.plus(Duration.ofMinutes(30));
        Instant appointmentEnd = appointmentStart.plus(Duration.ofMinutes(30));
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.update("insert into appointments (id,patient_profile_id,service_id,doctor_profile_id,status,booking_source,purpose,start_time,end_time,duration_minutes,buffer_minutes,created_by_account_id) " +
                            "values (?, ?, ?, ?, 'CONFIRMED', 'RECEPTIONIST_ASSISTED', 't032 checked in', ?, ?, 30, 10, ?)",
                    appointmentId, patient, serviceId, doctorId, timestamp(appointmentStart), timestamp(appointmentEnd), adminId);
            jdbc.update("insert into visits (id,patient_profile_id,source,originating_appointment_id,arrived_at,checked_in_at,created_by_account_id,status) " +
                            "values (?,?,'SCHEDULED',?,?,?,?,'OPEN')",
                    visitId, patient, appointmentId, timestamp(NOW), timestamp(NOW), adminId);
            jdbc.update("update appointments set status='CHECKED_IN', visit_id=? where id=?", visitId, appointmentId);
            jdbc.update("insert into visit_check_ins (visit_id,receptionist_account_id,reception_type,arrived_at,checked_in_at) " +
                            "values (?,?,'APPOINTMENT_CHECK_IN',?,?)",
                    visitId, receptionistId, timestamp(NOW), timestamp(NOW));
        });

        mvc.perform(post("/api/v1/admin/doctors/{doctorId}/time-offs", doctorId)
                        .with(actor(AccountRole.ADMIN, adminId)).with(csrf()).contentType("application/json")
                        .content("{\"startsAt\":\"2026-10-20T09:20:00Z\",\"endsAt\":\"2026-10-20T10:10:00Z\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_MUTATION_BLOCKED"))
                .andExpect(jsonPath("$.error.impact.resourceType").value("TIME_OFF"))
                .andExpect(jsonPath("$.error.impact.affectedBookingCount").value(1));
        assertEquals("CHECKED_IN", jdbc.queryForObject("select status from appointments where id=?", String.class, appointmentId));
        assertEquals(0, jdbc.queryForObject("select count(*) from doctor_time_off where doctor_profile_id=?", Integer.class, doctorId));
        assertEquals(0, jdbc.queryForObject("select count(*) from staff_resource_audits where doctor_profile_id=? and resource_type='TIME_OFF'", Integer.class, doctorId));
    }

    @Test
    void mutationWaitsForGuardThenRechecksNewBookingAndRejectsWithoutWrites() throws Exception {
        createFixtures();
        jdbc.update("insert into doctor_shifts (id,doctor_profile_id,starts_at,ends_at) values (?,?,?,?)",
                shiftId, doctorId, timestamp(NOW), timestamp(NOW.plus(Duration.ofHours(2))));
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        CountDownLatch guardHeld = new CountDownLatch(1);
        CountDownLatch releaseWriter = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> bookingWriter = pool.submit(() -> tx.executeWithoutResult(status -> {
                guard.acquire();
                guardHeld.countDown();
                await(releaseWriter);
                jdbc.update("insert into appointments (id,patient_profile_id,service_id,doctor_profile_id,status,booking_source,purpose,start_time,end_time,duration_minutes,buffer_minutes,created_by_account_id) " +
                                "values (?, ?, ?, ?, 'CONFIRMED', 'RECEPTIONIST_ASSISTED', 't032 race', ?, ?, 30, 10, ?)",
                        newBookingId(), patientId(), serviceId, doctorId, timestamp(NOW.plus(Duration.ofMinutes(30))),
                        timestamp(NOW.plus(Duration.ofMinutes(60))), adminId);
            }));
            assertTrue(guardHeld.await(5, TimeUnit.SECONDS), "booking writer should acquire T014 first");
            Future<?> mutation = pool.submit(() -> tx.executeWithoutResult(status -> {
                staffResources.deleteShift(doctorId, shiftId, adminPrincipal(), "t032-race-mutation");
            }));
            awaitDatabaseGuardLockWait();
            assertTrue(!mutation.isDone(), "mutation should wait on the same T014 guard");
            releaseWriter.countDown();
            bookingWriter.get(10, TimeUnit.SECONDS);
            try {
                mutation.get(10, TimeUnit.SECONDS);
                throw new AssertionError("expected protected mutation to be rejected");
            } catch (java.util.concurrent.ExecutionException failure) {
                assertTrue(failure.getCause() instanceof RuntimeException);
                assertEquals("RESOURCE_MUTATION_BLOCKED", failure.getCause().getMessage());
            }
            assertEquals(1, jdbc.queryForObject("select count(*) from doctor_shifts where id=?", Integer.class, shiftId));
            assertEquals(0, jdbc.queryForObject("select count(*) from staff_resource_audits where resource_id=? and action='DELETED'", Integer.class, shiftId));
            assertEquals(1, jdbc.queryForObject("select count(*) from appointments where doctor_profile_id=? and status='CONFIRMED'", Integer.class, doctorId));
        } finally {
            releaseWriter.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void assignmentRemovalWaitsForGuardThenRechecksNewActiveCare() throws Exception {
        createFixtures();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        CountDownLatch guardHeld = new CountDownLatch(1);
        CountDownLatch releaseWriter = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        UUID visitId = UUID.randomUUID();
        visitIds.add(visitId);
        try {
            Future<?> careWriter = pool.submit(() -> tx.executeWithoutResult(status -> {
                guard.acquire();
                guardHeld.countDown();
                await(releaseWriter);
                UUID patient = patientId();
                jdbc.update("insert into visits (id,patient_profile_id,source,arrived_at,checked_in_at,created_by_account_id,status) values (?,?,'WALK_IN',?, ?, ?, 'OPEN')",
                        visitId, patient, timestamp(NOW), timestamp(NOW), adminId);
                jdbc.update("insert into visit_assignments (id,visit_id,doctor_profile_id,starts_at,ends_at,status) values (?,?,?, ?, ?, 'IN_PROGRESS')",
                        UUID.randomUUID(), visitId, doctorId, timestamp(NOW), timestamp(NOW.plus(Duration.ofMinutes(30))));
            }));
            assertTrue(guardHeld.await(5, TimeUnit.SECONDS), "care writer should acquire T014 first");
            Future<?> mutation = pool.submit(() -> tx.executeWithoutResult(status ->
                    assignments.remove(serviceId, assignmentId, adminPrincipal(), "t032-race-assignment")));
            awaitDatabaseGuardLockWait();
            assertTrue(!mutation.isDone(), "assignment mutation should wait on the same T014 guard");
            releaseWriter.countDown();
            careWriter.get(10, TimeUnit.SECONDS);
            try {
                mutation.get(10, TimeUnit.SECONDS);
                throw new AssertionError("expected assignment removal to be rejected by new active care");
            } catch (java.util.concurrent.ExecutionException failure) {
                assertTrue(failure.getCause() instanceof RuntimeException);
                assertEquals("RESOURCE_MUTATION_BLOCKED", failure.getCause().getMessage());
            }
            assertEquals(1, jdbc.queryForObject("select count(*) from doctor_services where id=? and active", Integer.class, assignmentId));
            assertEquals(0, jdbc.queryForObject("select count(*) from service_audits where resource_id=? and action='DELETED'", Integer.class, assignmentId));
            assertEquals(1, jdbc.queryForObject("select count(*) from visit_assignments where visit_id=? and status='IN_PROGRESS'", Integer.class, visitId));
        } finally {
            releaseWriter.countDown();
            pool.shutdownNow();
        }
    }

    private UUID patientId() {
        UUID account = UUID.randomUUID();
        UUID patient = UUID.randomUUID();
        patientAccountIds.add(account);
        patientIds.add(patient);
        jdbc.update("insert into accounts (id,normalized_email,account_type,status,created_at,updated_at) values (?,?,'PATIENT','ACTIVE',now(),now())",
                account, "t032-patient-" + account + "@example.test");
        jdbc.update("insert into patient_profiles (id,account_id,full_name,phone_canonical,phone_owner_type,date_of_birth,gender) values (?,?,?,?,'PATIENT','1990-01-01','OTHER')",
                patient, account, "T032 Patient", "+849" + account.toString().replace("-", "").substring(0, 8));
        return patient;
    }

    private UUID newBookingId() {
        UUID id = UUID.randomUUID();
        appointmentIds.add(id);
        return id;
    }

    private Timestamp timestamp(Instant instant) { return Timestamp.from(instant); }

    private void createFixtures() {
        adminId = createAccount(AccountType.ADMIN, "admin");
        doctorAccountId = createAccount(AccountType.DOCTOR, "doctor");
        receptionistId = createAccount(AccountType.RECEPTIONIST, "receptionist");
        doctorId = UUID.randomUUID(); serviceId = UUID.randomUUID(); assignmentId = UUID.randomUUID();
        shiftId = UUID.randomUUID();
        jdbc.update("insert into doctor_profiles (id,account_id,full_name,work_contact,specialties,qualifications,biography,consultation_information,publicly_visible,accepting_new_bookings,in_active_directory) values (?,?,?,'t032-contact','test','','','',true,true,true)",
                doctorId, doctorAccountId, "Dr T032 " + doctorId);
        jdbc.update("insert into clinic_services (id,name,duration_minutes,buffer_minutes,online_bookable,active) values (?,? ,30,10,true,true)",
                serviceId, "T032 service " + serviceId);
        jdbc.update("insert into doctor_services (id,doctor_profile_id,service_id,active) values (?,?,?,true)",
                assignmentId, doctorId, serviceId);
    }

    private UUID createAccount(AccountType type, String label) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into accounts (id,normalized_email,account_type,status,created_at,updated_at) values (?,?,?,'ACTIVE',now(),now())",
                id, "t032-" + label + "-" + id + "@example.test", type.name());
        return id;
    }

    private AuthenticatedAccount adminPrincipal() {
        return new AuthenticatedAccount(adminId, AccountRole.ADMIN, UUID.randomUUID());
    }

    private RequestPostProcessor actor(AccountRole role, UUID accountId) {
        var principal = new AuthenticatedAccount(accountId, role, UUID.randomUUID());
        return authentication(new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    private Flyway flyway(String schema, String target) {
        return Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target(target).load();
    }

    private String createSchema(String prefix) {
        String schema = prefix + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("create schema " + schema);
        return schema;
    }

    private void dropSchema(String schema) { jdbc.execute("drop schema if exists " + schema + " cascade"); }
    private int tableCount(String schema, String name) {
        return jdbc.queryForObject("select count(*) from information_schema.tables where table_schema=? and table_name=?", Integer.class, schema, name);
    }
    private int triggerCount(String schema, String name) {
        return jdbc.queryForObject("select count(*) from information_schema.triggers where trigger_schema=? and trigger_name=?", Integer.class, schema, name);
    }
    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("timed out waiting for concurrent test signal");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while awaiting concurrent test signal", interrupted);
        }
    }

    private void awaitDatabaseGuardLockWait() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (System.nanoTime() < deadline) {
            Integer waiting = jdbc.queryForObject("select count(*) from pg_stat_activity "
                    + "where wait_event_type='Lock' and query ilike '%transaction_guard%'", Integer.class);
            if (waiting != null && waiting > 0) return;
            try {
                Thread.sleep(25);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while checking PostgreSQL lock wait", interrupted);
            }
        }
        throw new AssertionError("mutation did not wait on the T014 coordination guard in PostgreSQL");
    }
}
