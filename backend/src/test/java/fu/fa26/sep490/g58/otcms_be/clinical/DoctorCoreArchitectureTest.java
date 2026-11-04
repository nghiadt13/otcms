package fu.fa26.sep490.g58.otcms_be.clinical;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class DoctorCoreArchitectureTest {
    private final Path root = Path.of("src/main/java/fu/fa26/sep490/g58/otcms_be");

    @Test void ownerPersistenceDoesNotLeakAcrossRepairedFeatureBoundaries() throws Exception {
        var forbidden = Map.of("patient", "visit", "visit", "clinical", "clinical", "diagnostic");
        for (var boundary : forbidden.entrySet()) check(boundary.getKey(), boundary.getValue());
        check("diagnostic", "clinical"); check("diagnostic", "visit");
    }
    private void check(String consumer, String owner) throws Exception {
        var pattern = Pattern.compile("(?m)^import\\s+(?:static\\s+)?fu\\.fa26\\.sep490\\.g58\\.otcms_be\\."
                + owner + "\\.(repository|model\\.entity)\\.");
        try (var paths = Files.walk(root.resolve(consumer))) {
            for (var source : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
                assertFalse(pattern.matcher(Files.readString(source)).find(), source.toString());
            }
        }
    }

    @Test void visitProductionNoLongerReadsLegacyClinicalTable() throws Exception {
        try (var paths = Files.walk(root.resolve("visit"))) {
            for (var source : paths.filter(path -> path.toString().endsWith(".java")).toList())
                assertFalse(Files.readString(source).contains("visit_examinations"), source.toString());
        }
    }
}
