package fu.fa26.sep490.g58.otcms_be.common.web;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import fu.fa26.sep490.g58.otcms_be.config.ProductionReadinessProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;
import org.springframework.util.StringUtils;

/** Immutable startup-evaluated production command gate, enabled only by deployment configuration. */
public final class ProductionReadinessGate {
    private static final Set<String> REQUIRED_PREREQUISITES = Set.of(
            "buildMigrationVerified", "databaseConstraintsVerified", "roomCapabilitiesProvisioned",
            "operationalConfigurationVerified", "phase7Passed");
    private static final Set<String> REQUIRED_ENDPOINT_GROUPS = Set.of(
            "appointment-commands", "visit-walk-ins", "visit-care-commands",
            "doctor-follow-up-appointments", "admin-resource-mutations");
    private final boolean closed;

    private ProductionReadinessGate(boolean closed) { this.closed = closed; }

    public static ProductionReadinessGate load(ProductionReadinessProperties properties) {
        return load(properties, Clock.systemUTC(), new ObjectMapper());
    }

    public static ProductionReadinessGate load(ProductionReadinessProperties properties, Clock clock, ObjectMapper mapper) {
        if (!properties.isRequired()) return new ProductionReadinessGate(false);
        try {
            if (!StringUtils.hasText(properties.getEvidenceFile())
                    || !properties.getEvidenceSha256().matches("(?i)[0-9a-f]{64}")
                    || !StringUtils.hasText(properties.getEvidenceSignature())
                    || !StringUtils.hasText(properties.getVerificationPublicKey())
                    || !StringUtils.hasText(properties.getReleaseId())
                    || !StringUtils.hasText(properties.getMigrationVersion())
                    || !StringUtils.hasText(properties.getExpectedOwner())) return new ProductionReadinessGate(true);
            byte[] bytes = Files.readAllBytes(Path.of(properties.getEvidenceFile()));
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if (!MessageDigest.isEqual(digest.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                    properties.getEvidenceSha256().toLowerCase().getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
                return new ProductionReadinessGate(true);
            }
            byte[] encodedKey = java.util.Base64.getDecoder().decode(properties.getVerificationPublicKey());
            var publicKey = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(encodedKey));
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(publicKey);
            verifier.update(bytes);
            if (!verifier.verify(java.util.Base64.getDecoder().decode(properties.getEvidenceSignature()))) {
                return new ProductionReadinessGate(true);
            }
            JsonNode evidence = mapper.readTree(bytes);
            Instant expiresAt = Instant.parse(text(evidence, "expiresAt"));
            JsonNode prerequisites = evidence.path("prerequisites");
            boolean allPrerequisites = REQUIRED_PREREQUISITES.stream().allMatch(key -> prerequisites.path(key).asBoolean(false));
            boolean commandEndpoints = evidence.path("protectedEndpointGroups").isArray()
                    && containsAll(evidence.path("protectedEndpointGroups"), REQUIRED_ENDPOINT_GROUPS);
            boolean versionMatches = properties.getReleaseId().equals(text(evidence, "releaseId"))
                    && properties.getMigrationVersion().equals(text(evidence, "migrationVersion"));
            boolean ownerMatches = properties.getExpectedOwner().equals(text(evidence, "approvedBy"));
            return new ProductionReadinessGate(!(expiresAt.isAfter(clock.instant()) && allPrerequisites
                    && commandEndpoints && versionMatches && ownerMatches));
        } catch (IOException | RuntimeException | java.security.GeneralSecurityException failure) {
            return new ProductionReadinessGate(true);
        }
    }

    public boolean isClosed() { return closed; }

    public boolean protects(String method, String path) {
        if (!Set.of("POST", "PUT", "PATCH", "DELETE").contains(method.toUpperCase())) return false;
        if (path.equals("/api/v1/appointments") || path.equals("/api/v1/visits/walk-ins")) return true;
        if (path.matches("/api/v1/admin/doctors/[^/]+/mutation-impact")
                || path.matches("/api/v1/admin/doctors/[^/]+/time-offs/[^/]+/mutation-impact")) return false;
        return path.equals("/api/v1/admin/doctor-profiles") || path.startsWith("/api/v1/admin/doctors/")
                || path.equals("/api/v1/admin/clinic-services")
                || path.startsWith("/api/v1/admin/clinic-services/")
                || path.startsWith("/api/v1/admin/visit-rooms/") || path.equals("/api/v1/admin/visit-rooms")
                || path.matches("/api/v1/visits/[^/]+/care-assignments(?:/[^/]+/(?:extensions|completion))?")
                || path.matches("/api/v1/visits/[^/]+/follow-up-appointments")
                || path.matches("/api/v1/appointments/[^/]+/(?:status|check-in|reassign|cancel|reschedule)");
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual()) throw new IllegalArgumentException("Missing readiness field");
        return value.textValue();
    }

    private static boolean containsAll(JsonNode values, Set<String> expected) {
        Set<String> actual = new java.util.HashSet<>();
        values.forEach(value -> { if (value.isTextual()) actual.add(value.textValue()); });
        return actual.containsAll(expected);
    }

}
