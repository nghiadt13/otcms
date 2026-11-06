package fu.fa26.sep490.g58.otcms_be.visit;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class VisitDiagnosticArchitectureTest {
    private final Path root = Path.of("src/main/java/fu/fa26/sep490/g58/otcms_be");
    @Test void ownerPackagesNeverImportEachOthersPersistence() throws Exception {
        for (var boundary : List.of(new String[]{"visit", "diagnostic"}, new String[]{"diagnostic", "visit"},
                new String[]{"clinical", "imaging"})) {
            try (var files = Files.walk(root.resolve(boundary[0]))) {
                for (var file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    String source = Files.readString(file);
                    assertFalse(source.contains("otcms_be." + boundary[1] + ".repository."), file.toString());
                    // Existing Diagnostic outcome enums are transport-independent values, not JPA entities.
                    assertFalse(java.util.regex.Pattern.compile("import .*otcms_be\\." + boundary[1]
                            + "\\.model\\.entity\\.(?!enums\\.)").matcher(source).find(), file.toString());
                }
            }
        }
    }
    @Test void coordinationOnlyWritesReceiptsAndCannotFinishClinicalWork() throws Exception {
        String service = Files.readString(root.resolve("visit/service/impl/VisitImagingCoordinationServiceImpl.java"));
        for (String forbidden : List.of("visits.save", "setStatus", "ClinicalExamination", "Invoice", "Prescription", "queueNumber")) {
            assertFalse(service.contains(forbidden), forbidden);
        }
        assertTrue(service.contains("Propagation.REQUIRES_NEW"));
        String query = Files.readString(root.resolve("diagnostic/service/impl/DiagnosticResultEventQueryServiceImpl.java"));
        assertFalse(query.contains(".save("));
        assertFalse(query.contains(".saveAndFlush("));
    }
    @Test void completionEvidenceIsReadOnlyDiagnosticOwnedAndVisitUsesOnlyPublicContract() {
        var implementation = fu.fa26.sep490.g58.otcms_be.diagnostic.service.impl.DiagnosticVisitCompletionEvidenceServiceImpl.class;
        assertTrue(implementation.getAnnotation(org.springframework.transaction.annotation.Transactional.class).readOnly());
        for (var dependency : implementation.getDeclaredFields()) {
            assertEquals("fu.fa26.sep490.g58.otcms_be.diagnostic.repository", dependency.getType().getPackageName());
        }
        assertTrue(java.util.Arrays.stream(fu.fa26.sep490.g58.otcms_be.visit.service.impl.WalkInVisitLifecycleServiceImpl.class.getDeclaredFields())
                .anyMatch(f -> f.getType() == fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticVisitCompletionEvidenceService.class));
    }

}
