package fu.fa26.sep490.g58.otcms_be.visit;

import static org.junit.jupiter.api.Assertions.*;

import fu.fa26.sep490.g58.otcms_be.visit.model.entity.QueueEntryJpaEntity;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReturningServiceQueueEntryTest {
    @Test void returningServiceGetsFreshQueuePosition() {
        var oldTime = Instant.parse("2026-11-01T01:00:00Z");
        var now = oldTime.plusSeconds(86400);
        var entry = QueueEntryJpaEntity.service(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), LocalDate.parse("2026-11-01"), 3, 10, oldTime);
        entry.cancelStay(oldTime);
        UUID room = UUID.randomUUID();
        entry.rejoinRoomQueue(room, LocalDate.parse("2026-11-02"), 5, now);
        assertEquals("WAITING", entry.getStatus());
        assertEquals(room, entry.getRoomId());
        assertEquals(5, entry.getQueueNumber());
        assertEquals(now.toEpochMilli(), entry.getSortOrder());
        assertNull(entry.getFinishedAt());
    }
}
