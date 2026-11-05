package fu.fa26.sep490.g58.otcms_be.visit;

import static org.junit.jupiter.api.Assertions.*;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitImagingResultReceiptRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Dedicated local test database; rollback fixtures. Not executed when PostgreSQL is unavailable. */
@SpringBootTest @LocalPostgresTest @Transactional
class VisitImagingResultReceiptPostgresIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired VisitImagingResultReceiptRepository receipts;
    final UUID account = UUID.randomUUID(), patient = UUID.randomUUID(), visit = UUID.randomUUID(), event = UUID.randomUUID(), request = UUID.randomUUID();
    @BeforeEach void fixture() {
        jdbc.update("insert into accounts (id,normalized_email,account_type,status,created_at,updated_at) values (?,?,'PATIENT','ACTIVE',now(),now())", account, "coord-" + account + "@example.test");
        jdbc.update("insert into patient_profiles (id,account_id,full_name,phone_canonical,phone_owner_type,date_of_birth,gender) values (?,?,'Coordination fixture','+84977774444','PATIENT','1990-01-01','OTHER')", patient, account);
        jdbc.update("insert into visits (id,patient_profile_id,source,arrived_at,checked_in_at,created_by_account_id,status) values (?,?,'WALK_IN',now(),now(),?,'OPEN')", visit, patient, account);
    }
    void insert() { assertEquals(1, receipts.insertReceipt(event, request, visit, patient, "FINDINGS_RELEASE", "a".repeat(64), Instant.now())); }
    @Test void replayKeepsSingleImmutableReceiptAndOriginalHash() {
        insert();
        assertEquals(0, receipts.insertReceipt(event, request, visit, patient, "FINDINGS_RELEASE", "a".repeat(64), Instant.now()));
        assertEquals(0, receipts.insertReceipt(event, request, visit, patient, "FINDINGS_RELEASE", "b".repeat(64), Instant.now()));
        assertEquals("a".repeat(64), receipts.findById(event).orElseThrow().getPayloadSha256());
        assertEquals(1, jdbc.queryForObject("select count(*) from visit_imaging_result_receipts where event_id=?", Integer.class, event));
        assertEquals("OPEN", jdbc.queryForObject("select status from visits where id=?", String.class, visit));
    }
    @Test void acknowledgedReceiptCannotBeUpdated() {
        insert(); assertThrows(DataAccessException.class, () -> jdbc.update("update visit_imaging_result_receipts set payload_sha256=? where event_id=?", "b".repeat(64), event));
    }
    @Test void nonexistentVisitCannotReceiveAcknowledgement() {
        assertThrows(DataAccessException.class, () -> receipts.insertReceipt(event, request, UUID.randomUUID(), patient, "FINDINGS_RELEASE", "a".repeat(64), Instant.now()));
    }
}
