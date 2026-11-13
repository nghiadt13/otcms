package fu.fa26.sep490.g58.otcms_be.visit;

import static org.junit.jupiter.api.Assertions.*;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import fu.fa26.sep490.g58.otcms_be.visit.service.ImagingRoomQueueService;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@LocalPostgresTest
@Transactional
class ImagingRoomQueuePostgresIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @Autowired ImagingRoomQueueService rooms;
    @Autowired Clock clock;
    @Test void realRoomServiceCatalogAndStaffQueriesEnforceQueueAdmission() {
        UUID room = UUID.randomUUID(), service = UUID.randomUUID(), actor = UUID.randomUUID(), profile = UUID.randomUUID();
        var now = clock.instant();
        jdbc.update("insert into clinic_services(id,name,service_type,modality,duration_minutes,buffer_minutes,price) values (?,?,'IMAGING','MRI',23,7,100)", service, "Queue MRI " + service);
        jdbc.update("insert into rooms(id,room_code,display_label,room_type) values (?,?,'Queue room','IMAGING')", room, "QUEUE-" + room);
        jdbc.update("insert into room_supported_services(room_id,service_id) values (?,?)", room, service);
        jdbc.update("update rooms set capabilities = array['IMAGING_ROOM'] where id=?", room);
        jdbc.update("insert into accounts(id,normalized_email,account_type,status,created_at,updated_at) values (?,?,'IMAGING_TECHNICIAN','ACTIVE',now(),now())", actor, actor + "@queue.test");
        jdbc.update("insert into staff_profiles(id,account_id,display_name,work_contact) values (?,?,'Queue technician','')", profile, actor);
        jdbc.update("insert into work_shifts(staff_profile_id,room_id,starts_at,ends_at) values (?,?,?,?)", profile, room, Timestamp.from(now.minusSeconds(60)), Timestamp.from(now.plusSeconds(3600)));
        assertEquals(room, rooms.findEligibleRooms(List.of(service)).get(service).getFirst().roomId());
        jdbc.update("update rooms set active=false where id=?", room);
        entityManager.clear();
        assertTrue(rooms.findEligibleRooms(List.of(service)).isEmpty());
        jdbc.update("update rooms set active=true,room_type='EXAMINATION' where id=?", room);
        entityManager.clear();
        assertTrue(rooms.findEligibleRooms(List.of(service)).isEmpty());
        jdbc.update("update rooms set room_type='IMAGING' where id=?", room);
        jdbc.update("update rooms set capabilities = '{}' where id=?", room);
        entityManager.clear();
        assertTrue(rooms.findEligibleRooms(List.of(service)).isEmpty());
        jdbc.update("update rooms set capabilities = array['IMAGING_ROOM'] where id=?", room);
        jdbc.update("update accounts set status='DISABLED' where id=?", actor);
        entityManager.clear();
        assertTrue(rooms.findEligibleRooms(List.of(service)).isEmpty());
        assertEquals(0, jdbc.queryForObject("select count(*) from room_reservations where room_id=?", Integer.class, room));
    }
}
