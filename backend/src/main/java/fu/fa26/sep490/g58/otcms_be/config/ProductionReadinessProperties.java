package fu.fa26.sep490.g58.otcms_be.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "appointment.booking.readiness")
public class ProductionReadinessProperties {
    private boolean required = true;
    private String evidenceFile = "";
    private String evidenceSha256 = "";
    private String evidenceSignature = "";
    private String verificationPublicKey = "";
    private String releaseId = "";
    private String migrationVersion = "";
    private String expectedOwner = "Owner Long";

    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }
    public String getEvidenceFile() { return evidenceFile; }
    public void setEvidenceFile(String evidenceFile) { this.evidenceFile = evidenceFile; }
    public String getEvidenceSha256() { return evidenceSha256; }
    public void setEvidenceSha256(String evidenceSha256) { this.evidenceSha256 = evidenceSha256; }
    public String getEvidenceSignature() { return evidenceSignature; }
    public void setEvidenceSignature(String evidenceSignature) { this.evidenceSignature = evidenceSignature; }
    public String getVerificationPublicKey() { return verificationPublicKey; }
    public void setVerificationPublicKey(String verificationPublicKey) { this.verificationPublicKey = verificationPublicKey; }
    public String getReleaseId() { return releaseId; }
    public void setReleaseId(String releaseId) { this.releaseId = releaseId; }
    public String getMigrationVersion() { return migrationVersion; }
    public void setMigrationVersion(String migrationVersion) { this.migrationVersion = migrationVersion; }
    public String getExpectedOwner() { return expectedOwner; }
    public void setExpectedOwner(String expectedOwner) { this.expectedOwner = expectedOwner; }
}
