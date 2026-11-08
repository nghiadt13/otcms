package fu.fa26.sep490.g58.otcms_be.clinical;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ClinicalMedicalDocumentArchitectureTest {
    @Test void medicalDocumentsUseOnlyPublicCrossContextCapabilitiesAndNoRoomOrQueueDependency() throws Exception {
        try (var paths = Files.walk(Path.of("src/main/java/fu/fa26/sep490/g58/otcms_be/clinical"))) {
            for (var path : paths.filter(file -> file.getFileName().toString().contains("MedicalDocument") && file.toString().endsWith(".java")).toList()) {
                String code = Files.readString(path);
                for (String forbidden : List.of("visit.repository", "visit.model.entity", "patient.repository", "patient.model.entity", "diagnostic.repository",
                        "diagnostic.model.entity", "billing.repository", "roomQueue", "queueNumber", "waitingPosition", "ManageVisitRoom", "RoomCapacity"))
                    assertFalse(code.contains(forbidden), path + ": " + forbidden);
            }
        }
    }
    @Test void replacementAndReleaseUseRequiredTransactionAndGuardVersionAndUnreleasedState() throws Exception {
        var implementation = fu.fa26.sep490.g58.otcms_be.clinical.service.impl.ClinicalMedicalDocumentServiceImpl.class;
        for (var method : implementation.getDeclaredMethods()) {
            if (!List.of("upload", "update", "release").contains(method.getName())) continue;
            assertEquals(org.springframework.transaction.annotation.Propagation.REQUIRED,
                    method.getAnnotation(org.springframework.transaction.annotation.Transactional.class).propagation());
        }
        for (var method : fu.fa26.sep490.g58.otcms_be.clinical.repository.ClinicalMedicalDocumentRepository.class.getDeclaredMethods()) {
            if (!List.of("replace", "release").contains(method.getName())) continue;
            String query = method.getAnnotation(org.springframework.data.jpa.repository.Query.class).value();
            assertTrue(query.contains("d.version = :version")); assertTrue(query.contains("d.releasedAt is null"));
        }
    }
}
