package fu.fa26.sep490.g58.otcms_be.diagnostic.model;

import static org.junit.jupiter.api.Assertions.*;

import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.LaboratorySampleJpaEntity;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LaboratorySampleJpaEntityTest {
    private final UUID actor = UUID.randomUUID(), order = UUID.randomUUID();
    private final Instant collected = Instant.parse("2026-11-17T00:00:00Z");

    @Test void recordsRejectionWithoutErasingCollectionEvidence() {
        var sample = sample();
        sample.reject("Mẫu không đạt", "Đối tác", actor, collected.plusSeconds(120));
        assertEquals("LAB-001", sample.getSampleCode());
        assertEquals(collected, sample.getCollectedAt());
        assertEquals("Đối tác", sample.getRejectionSource());
        assertThrows(IllegalStateException.class, () -> sample.reject("Khác", "Lab", actor, collected.plusSeconds(180)));
    }

    @Test void rejectsInvalidTimesAndUnconfirmedResponsibleParty() {
        var sample = sample();
        assertThrows(IllegalStateException.class, () -> sample.reject("Lỗi", "Lab", actor, collected.minusSeconds(1)));
        assertThrows(IllegalStateException.class, () -> sample.reject("Lỗi", " ", actor, collected));
    }

    private LaboratorySampleJpaEntity sample() {
        return new LaboratorySampleJpaEntity(UUID.randomUUID(), order, 1, "LAB-001", actor,
                collected.minusSeconds(30), actor, collected);
    }
}
