package fu.fa26.sep490.g58.otcms_be.clinical;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ClinicalPastMedicalRecordsArchitectureTest {
    @Test void historyCompositionUsesPublicOwnerServicesAndNoBusinessMutation() throws Exception {
        Path root = Path.of("src/main/java/fu/fa26/sep490/g58/otcms_be/clinical");
        for (String name : List.of("controller/ClinicalPastMedicalRecordsController.java", "service/impl/ClinicalPastMedicalRecordsServiceImpl.java")) {
            String source = Files.readString(root.resolve(name));
            for (String forbidden : List.of("visit.repository", "visit.model.entity", "diagnostic.repository", "diagnostic.model.entity",
                    "patient.repository", "patient.model.entity", "billing.repository", "RoomQueue", "queueNumber", "waitingPosition", ".save(", ".delete(")) {
                assertFalse(source.contains(forbidden), name + " must not reference " + forbidden);
            }
        }
    }
    @Test void metadataHistoryCannotLoadPayloadAndAddsNoHistoryPersistence() throws Exception {
        String source = Files.readString(Path.of("src/main/java/fu/fa26/sep490/g58/otcms_be/clinical/service/impl/ClinicalMedicalDocumentHistoryQueryServiceImpl.java"));
        assertFalse(source.contains("Payload")); assertFalse(source.contains("byte[]"));
        try (var files = Files.list(Path.of("src/main/resources/db/migration"))) {
            assertTrue(files.noneMatch(p -> p.getFileName().toString().matches("V101__.*history.*")));
        }
    }
}
