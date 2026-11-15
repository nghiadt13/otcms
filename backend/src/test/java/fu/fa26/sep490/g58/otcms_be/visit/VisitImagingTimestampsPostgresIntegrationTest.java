package fu.fa26.sep490.g58.otcms_be.visit;

import static org.junit.jupiter.api.Assertions.*;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import fu.fa26.sep490.g58.otcms_be.visit.repository.ClinicVisitJpaRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Visit timestamps retain the first acknowledgement. Event/hash replay is owned by imaging study lifecycle. */
@SpringBootTest @LocalPostgresTest @Transactional
class VisitImagingTimestampsPostgresIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ClinicVisitJpaRepository visits;
    final UUID account = UUID.randomUUID(), patient = UUID.randomUUID(), visit = UUID.randomUUID();
    @BeforeEach void fixture() {
        jdbc.update("insert into accounts (id,normalized_email,account_type,status,created_at,updated_at) values (?,?,'PATIENT','ACTIVE',now(),now())", account, "coord-" + account + "@example.test");
        jdbc.update("insert into patient_profiles (id,account_id,full_name,phone_canonical,phone_owner_type,date_of_birth,gender) values (?,?,'Coordination fixture','+84977774444','PATIENT','1990-01-01','OTHER')", patient, account);
        jdbc.update("insert into visits (id,patient_profile_id,source,arrived_at,checked_in_at,created_by_account_id,status) values (?,?,'WALK_IN',now(),now(),?,'OPEN')", visit, patient, account);
    }
    @Test void releaseReplayKeepsFirstAcknowledgementTime() {
        Instant first = Instant.parse("2026-11-05T08:00:00Z");
        assertEquals(1, visits.markFindingsReleased(visit, patient, first));
        assertEquals(1, visits.markFindingsReleased(visit, patient, first.plusSeconds(60)));
        assertEquals(first, jdbc.queryForObject("select findings_released_at from visits where id=?", java.time.OffsetDateTime.class, visit).toInstant());
        assertEquals("OPEN", jdbc.queryForObject("select status from visits where id=?", String.class, visit));
    }
    @Test void resultReadyReplayKeepsFirstAcknowledgementTime() {
        Instant first = Instant.parse("2026-11-05T08:00:00Z");
        assertEquals(1, visits.markImagingResultReady(visit, patient, first));
        assertEquals(1, visits.markImagingResultReady(visit, patient, first.plusSeconds(60)));
        assertEquals(first, jdbc.queryForObject("select imaging_result_ready_at from visits where id=?", java.time.OffsetDateTime.class, visit).toInstant());
    }
    @Test void nonexistentVisitCannotReceiveAcknowledgement() {
        assertEquals(0, visits.markFindingsReleased(UUID.randomUUID(), patient, Instant.now()));
        assertEquals(0, visits.markFindingsReleased(visit, UUID.randomUUID(), Instant.now()));
        assertNull(jdbc.queryForObject("select findings_released_at from visits where id=?", java.time.OffsetDateTime.class, visit));
    }
}
