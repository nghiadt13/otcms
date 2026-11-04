package fu.fa26.sep490.g58.otcms_be.clinical;

import static org.junit.jupiter.api.Assertions.*;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.*;
import fu.fa26.sep490.g58.otcms_be.clinical.service.impl.ClinicalVisitCompletionEvidenceServiceImpl;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class ClinicalPrescriptionArchitectureTest {
    @Test void prescriptionSourcesHaveNoForeignPersistenceOrInventoryBillingOrRoomDependencies() throws Exception {
        try (var files = Files.walk(Path.of("src/main/java/fu/fa26/sep490/g58/otcms_be/clinical"))) {
            for (var path : files.filter(file -> file.getFileName().toString().contains("Prescription") && file.toString().endsWith(".java")).toList()) {
                String code = Files.readString(path);
                for (String forbidden : List.of("visit.repository", "visit.model.entity", "patient.repository", "patient.model.entity",
                        "staff.repository", "auth.repository", "diagnostic.repository", "billing", "inventory", "pharmacy", "queue", "room"))
                    assertFalse(code.contains(forbidden), path + ": " + forbidden);
            }
        }
    }
    @Test void prescriptionAbsenceDoesNotAddAnyVisitCompletionRequirement() {
        var examinations = mock(ClinicalExaminationRepository.class); var orders = mock(ServiceOrderRepository.class);
        var evidence = new ClinicalVisitCompletionEvidenceServiceImpl(examinations, orders).getCompletionEvidence(UUID.randomUUID());
        assertTrue(evidence.allNonCancelledOrdersHaveResults()); assertEquals(0, evidence.outstandingOrderCount());
        assertFalse(Arrays.stream(ClinicalVisitCompletionEvidenceServiceImpl.class.getDeclaredFields())
                .anyMatch(field -> field.getType().getName().contains("Prescription")));
    }
    @Test void writesDeclareRequiredAtomicTransactions() throws Exception {
        var implementation = fu.fa26.sep490.g58.otcms_be.clinical.service.impl.ClinicalPrescriptionServiceImpl.class;
        for (var method : implementation.getDeclaredMethods()) {
            if (!List.of("saveDraft", "issue").contains(method.getName())) continue;
            assertEquals(org.springframework.transaction.annotation.Propagation.REQUIRED,
                    method.getAnnotation(org.springframework.transaction.annotation.Transactional.class).propagation());
        }
    }
}
