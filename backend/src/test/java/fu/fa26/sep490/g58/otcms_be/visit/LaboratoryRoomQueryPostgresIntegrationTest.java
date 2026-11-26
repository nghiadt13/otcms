package fu.fa26.sep490.g58.otcms_be.visit;

import static org.junit.jupiter.api.Assertions.*;

import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import fu.fa26.sep490.g58.otcms_be.visit.service.LaboratoryRoomQueryService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@LocalPostgresTest
@Transactional
class LaboratoryRoomQueryPostgresIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @Autowired LaboratoryRoomQueryService rooms;

    @Test void eligibleRoomsMustBeActiveLaboratoryRoomsSupportingTheService() {
        UUID room = UUID.randomUUID(), service = UUID.randomUUID();
        jdbc.update("insert into clinic_services(id,name,service_type,duration_minutes,buffer_minutes,price) values (?,?,'LABORATORY',15,0,100)", service, "Lab " + service);
        jdbc.update("insert into rooms(id,room_code,display_label,room_type,capabilities) values (?,?,'Lab room','LABORATORY',array['LAB_ROOM'])", room, "LAB-" + room);
        jdbc.update("insert into room_supported_services(room_id,service_id) values (?,?)", room, service);
        assertTrue(rooms.isEligibleRoom(room, service));
        assertFalse(rooms.isEligibleRoom(room, UUID.randomUUID()));
        jdbc.update("update rooms set active=false where id=?", room);
        entityManager.clear();
        assertFalse(rooms.isEligibleRoom(room, service));
        jdbc.update("update rooms set active=true,capabilities='{}' where id=?", room);
        entityManager.clear();
        assertFalse(rooms.isEligibleRoom(room, service));
    }
}
