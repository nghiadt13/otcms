package fu.fa26.sep490.g58.otcms_be.imaging.service;

import static org.junit.jupiter.api.Assertions.*;

import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationException;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingAssignedWork;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationState;
import fu.fa26.sep490.g58.otcms_be.imaging.service.impl.ImagingSendService;
import fu.fa26.sep490.g58.otcms_be.imaging.service.impl.ImagingUploadRegistrationService;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@LocalPostgresTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ImagingUploadSendReplayPostgresIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    @Autowired ImagingUploadRegistrationService uploads;
    @Autowired ImagingSendService sends;

    final UUID account = UUID.randomUUID(), patient = UUID.randomUUID(), visit = UUID.randomUUID();
    final UUID order = UUID.randomUUID(), attempt = UUID.randomUUID(), actor = account, round = UUID.randomUUID();

    @BeforeEach void fixture() {
        jdbc.update("insert into accounts (id,normalized_email,account_type,status,created_at,updated_at) "
                + "values (?,?,'PATIENT','ACTIVE',now(),now())", account, "imaging-replay-" + account + "@example.test");
        jdbc.update("insert into patient_profiles (id,account_id,full_name,phone_canonical,phone_owner_type,date_of_birth,gender) "
                + "values (?,?,'Imaging replay fixture','+84977775555','PATIENT','1990-01-01','OTHER')", patient, account);
        jdbc.update("insert into visits (id,patient_profile_id,source,arrived_at,checked_in_at,created_by_account_id,status) "
                + "values (?,?,'WALK_IN',now(),now(),?,'OPEN')", visit, patient, account);
        jdbc.update("insert into service_order_rounds (id,visit_id,round_number,ordered_by_doctor_id,ordered_at,created_at) "
                + "values (?,?,1,?,now(),now())", round, visit, UUID.randomUUID());
        jdbc.update("insert into service_orders (id,order_code,ordering_round_id,"
                + "visit_id,patient_id,service_id,service_name_snapshot,service_type,requesting_doctor_id,"
                + "clinical_reason,body_area,price_snapshot,order_status,payment_status,created_at,updated_at,room_id) "
                + "values (?,?,?,?,?,?,'Replay MRI','IMAGING',?,'Test','KNEE',1,'ORDERED','PAID',now(),now(),?)",
                order, order.toString().replace("-", ""), round, visit, patient, UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID());
        jdbc.update("insert into imaging_executions (service_order_id,imaging_type,ordering_doctor_account_id,"
                + "execution_status,attempt_id,first_use_event_id,first_use_type,first_use_source_id,first_use_at,"
                + "created_at,updated_at) "
                + "values (?,'MRI',?,'IN_PROGRESS',?,?,'ACQUISITION_STARTED','fixture',now(),now(),now())",
                order, UUID.randomUUID(), attempt, UUID.randomUUID());
    }

    @AfterEach void cleanup() {
        jdbc.update("delete from idempotency_records where owner_id=? or resource_id in (select id from imaging_studies where service_order_id=?)", actor, order);
        jdbc.update("delete from imaging_studies where service_order_id=?", order);
        jdbc.update("delete from service_orders where id=?", order);
        jdbc.update("delete from service_order_rounds where id=?", round);
        jdbc.update("delete from visits where id=?", visit);
        jdbc.update("delete from patient_profiles where id=?", patient);
        jdbc.update("delete from accounts where id=?", account);
    }

    @Test void uploadSameKeyReplaysSameOperationAndChangedPayloadConflicts() {
        ImagingAssignedWork work = new ImagingAssignedWork(actor, order, attempt, visit, patient, "assignment-1", true);
        var first = uploads.register(actor, order, attempt, "upload-key", "a".repeat(64), 10, work);
        var retry = uploads.register(actor, order, attempt, "upload-key", "a".repeat(64), 10, work);
        assertTrue(first.created());
        assertFalse(retry.created());
        assertEquals(first.operation().id(), retry.operation().id());
        assertEquals(1, jdbc.queryForObject("select count(*) from idempotency_records where scope='IMAGING_UPLOAD' and owner_id=? and idempotency_key=?",
                Integer.class, actor, order + ":" + attempt + ":upload-key"));
        ImagingOperationException conflict = assertThrows(ImagingOperationException.class,
                () -> uploads.register(actor, order, attempt, "upload-key", "b".repeat(64), 10, work));
        assertEquals(ImagingOperationOutcome.IDEMPOTENCY_CONFLICT, conflict.outcome());
    }

    @Test void sendSameKeyReplaysStableEventAndDifferentKeyConflicts() {
        ImagingAssignedWork work = new ImagingAssignedWork(actor, order, attempt, visit, patient, "assignment-1", true);
        UUID operation = uploads.register(actor, order, attempt, "upload-key", "a".repeat(64), 10, work).operation().id();
        String study = "img_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.update("update imaging_studies set state='ARCHIVED_NOT_SENT', study_ref=?, verification_status='CONFIRMED_MATCH' where id=?",
                study, operation);
        entityManager.clear();
        var first = sends.send(actor, operation, "send-key");
        UUID event = jdbc.queryForObject("select confirmation_event_id from study_releases where imaging_study_id=?", UUID.class, operation);
        assertNotNull(event);
        var retry = sends.send(actor, operation, "send-key");
        assertEquals(ImagingOperationState.CONFIRMATION_PENDING, first.state());
        assertEquals(first.state(), retry.state());
        assertEquals(event, jdbc.queryForObject("select confirmation_event_id from study_releases where imaging_study_id=?", UUID.class, operation));
        assertEquals(1, jdbc.queryForObject("select count(*) from idempotency_records where scope='IMAGING_SEND' and owner_id=? and idempotency_key=?",
                Integer.class, operation, "send-key"));
        ImagingOperationException conflict = assertThrows(ImagingOperationException.class,
                () -> sends.send(actor, operation, "different-key"));
        assertEquals(ImagingOperationOutcome.IDEMPOTENCY_CONFLICT, conflict.outcome());
    }
}
