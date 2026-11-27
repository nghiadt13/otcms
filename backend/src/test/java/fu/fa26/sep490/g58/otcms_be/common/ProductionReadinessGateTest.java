package fu.fa26.sep490.g58.otcms_be.common;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fu.fa26.sep490.g58.otcms_be.common.web.ProductionReadinessGate;
import fu.fa26.sep490.g58.otcms_be.config.ProductionReadinessProperties;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class ProductionReadinessGateTest {
    private static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    private record SignedEvidence(ProductionReadinessProperties properties, Path file, KeyPair keys) { }

    private SignedEvidence createSignedEvidence(String evidenceJson) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair kp = kpg.generateKeyPair();
        byte[] bytes = evidenceJson.getBytes(StandardCharsets.UTF_8);
        Path file = tempDir.resolve("readiness.json");
        Files.write(file, bytes);

        String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        Signature sig = Signature.getInstance("Ed25519");
        sig.initSign(kp.getPrivate());
        sig.update(bytes);
        String signatureB64 = Base64.getEncoder().encodeToString(sig.sign());
        String pubKeyB64 = Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());

        ProductionReadinessProperties props = new ProductionReadinessProperties();
        props.setRequired(true);
        props.setEvidenceFile(file.toString());
        props.setEvidenceSha256(sha256);
        props.setEvidenceSignature(signatureB64);
        props.setVerificationPublicKey(pubKeyB64);
        props.setReleaseId("rel-1.0");
        props.setMigrationVersion("V025");
        props.setExpectedOwner("Owner Long");
        return new SignedEvidence(props, file, kp);
    }

    private static String validEvidenceJson(String expiresAt, String approvedBy,
            boolean phase7Passed, String endpointGroups) {
        return """
                {
                  "releaseId": "rel-1.0",
                  "migrationVersion": "V025",
                  "approvedBy": "%s",
                  "expiresAt": "%s",
                  "prerequisites": {
                    "buildMigrationVerified": true,
                    "databaseConstraintsVerified": true,
                    "roomCapabilitiesProvisioned": true,
                    "operationalConfigurationVerified": true,
                    "phase7Passed": %s
                  },
                  "protectedEndpointGroups": %s
                }
                """.formatted(approvedBy, expiresAt, phase7Passed, endpointGroups);
    }

    @Test
    void loadReturnsClosedGateWhenPropertiesAreNull() {
        assertTrue(ProductionReadinessGate.load(null, FIXED_CLOCK, MAPPER).isClosed());
        assertTrue(ProductionReadinessGate.load(null).isClosed());
    }

    @Test
    void loadReturnsOpenGateWhenNotRequired() {
        ProductionReadinessProperties props = new ProductionReadinessProperties();
        props.setRequired(false);
        assertFalse(ProductionReadinessGate.load(props, FIXED_CLOCK, MAPPER).isClosed());
    }

    @Test
    void loadReturnsOpenGateForValidSignedEd25519Evidence() throws Exception {
        String json = validEvidenceJson(
                NOW.plusSeconds(3600).toString(),
                "Owner Long",
                true,
                "[\"appointment-commands\",\"visit-walk-ins\",\"visit-care-commands\",\"doctor-follow-up-appointments\",\"admin-resource-mutations\"]");
        SignedEvidence signed = createSignedEvidence(json);

        ProductionReadinessGate gate = ProductionReadinessGate.load(signed.properties(), FIXED_CLOCK, MAPPER);

        assertFalse(gate.isClosed());
    }

    @Test
    void loadFailsClosedForExpiredTamperedWrongOwnerMissingPrereqAndMissingGroup() throws Exception {
        String allGroups = "[\"appointment-commands\",\"visit-walk-ins\",\"visit-care-commands\",\"doctor-follow-up-appointments\",\"admin-resource-mutations\"]";

        // Expired at exact clock instant
        SignedEvidence expired = createSignedEvidence(validEvidenceJson(NOW.toString(), "Owner Long", true, allGroups));
        assertTrue(ProductionReadinessGate.load(expired.properties(), FIXED_CLOCK, MAPPER).isClosed());

        // Tampered file content (digest mismatch)
        SignedEvidence tampered = createSignedEvidence(validEvidenceJson(NOW.plusSeconds(60).toString(), "Owner Long", true, allGroups));
        Files.writeString(tampered.file(), "{\"tampered\":true}");
        assertTrue(ProductionReadinessGate.load(tampered.properties(), FIXED_CLOCK, MAPPER).isClosed());

        // Invalid signature (wrong public key)
        SignedEvidence badSig = createSignedEvidence(validEvidenceJson(NOW.plusSeconds(60).toString(), "Owner Long", true, allGroups));
        KeyPair otherKey = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        badSig.properties().setVerificationPublicKey(Base64.getEncoder().encodeToString(otherKey.getPublic().getEncoded()));
        assertTrue(ProductionReadinessGate.load(badSig.properties(), FIXED_CLOCK, MAPPER).isClosed());

        // Wrong owner
        SignedEvidence wrongOwner = createSignedEvidence(validEvidenceJson(NOW.plusSeconds(60).toString(), "Someone Else", true, allGroups));
        assertTrue(ProductionReadinessGate.load(wrongOwner.properties(), FIXED_CLOCK, MAPPER).isClosed());

        // Missing prerequisite
        SignedEvidence missingPrereq = createSignedEvidence(validEvidenceJson(NOW.plusSeconds(60).toString(), "Owner Long", false, allGroups));
        assertTrue(ProductionReadinessGate.load(missingPrereq.properties(), FIXED_CLOCK, MAPPER).isClosed());

        // Missing endpoint group
        SignedEvidence missingGroup = createSignedEvidence(validEvidenceJson(
                NOW.plusSeconds(60).toString(), "Owner Long", true, "[\"appointment-commands\"]"));
        assertTrue(ProductionReadinessGate.load(missingGroup.properties(), FIXED_CLOCK, MAPPER).isClosed());

        // Missing required property text
        ProductionReadinessProperties incomplete = new ProductionReadinessProperties();
        incomplete.setRequired(true);
        assertTrue(ProductionReadinessGate.load(incomplete, FIXED_CLOCK, MAPPER).isClosed());
    }

    @Test
    void protectsHandlesNullInputsCaseInsensitiveMethodsAndEndpointRules() {
        ProductionReadinessGate gate = ProductionReadinessGate.load(null, FIXED_CLOCK, MAPPER);

        assertFalse(gate.protects(null, "/api/v1/appointments"));
        assertFalse(gate.protects("POST", null));
        assertFalse(gate.protects(null, null));
        assertFalse(gate.protects("GET", "/api/v1/appointments"));
        assertFalse(gate.protects("POST", "/api/v1/appointments/pending-hold/cancel"));
        assertFalse(gate.protects("POST", "/api/v1/admin/doctors/123/mutation-impact"));

        assertTrue(gate.protects("post", "/api/v1/appointments"));
        assertTrue(gate.protects("put", "/api/v1/admin/clinic-services/123"));
        assertTrue(gate.protects("patch", "/api/v1/visits/123/care-assignments/456/completion"));
        assertTrue(gate.protects("delete", "/api/v1/admin/visit-rooms/123"));
        assertTrue(gate.protects("POST", "/api/v1/visits/walk-ins"));
        assertTrue(gate.protects("POST", "/api/v1/visits/123/follow-up-appointments"));
    }
}
