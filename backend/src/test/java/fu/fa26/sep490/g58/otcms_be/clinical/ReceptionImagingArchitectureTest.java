package fu.fa26.sep490.g58.otcms_be.clinical;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ReceptionImagingArchitectureTest {
    @Test void billingAndClinicalUseContractsWithoutReadingEachOthersPersistence() throws Exception {
        Path root = Path.of("src/main/java/fu/fa26/sep490/g58/otcms_be");
        for (var pair : List.of(new String[]{"billing", "clinical"}, new String[]{"clinical", "billing"})) {
            try (var paths = Files.walk(root.resolve(pair[0]))) {
                for (var path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                    String source = Files.readString(path);
                    assertFalse(source.contains("otcms_be." + pair[1] + ".repository."), path.toString());
                    assertFalse(source.contains("otcms_be." + pair[1] + ".model.entity."), path.toString());
                }
            }
        }
    }
    @Test void doctorOrderTransactionHasNoDiagnosticRegistrationDependency() {
        var dependencies = fu.fa26.sep490.g58.otcms_be.clinical.service.impl.ClinicalImagingRequestTransactionService.class.getDeclaredFields();
        assertTrue(Arrays.stream(dependencies).noneMatch(field -> field.getType().getPackageName().contains("diagnostic")));
    }
}
