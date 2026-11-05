package fu.fa26.sep490.g58.otcms_be.imaging.config;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** External, clinic-specific DICOM-to-owner mappings. Never commit real identifiers here. */
@ConfigurationProperties(prefix = "otcms.imaging.identity")
public class ImagingIdentityProperties {
    private Map<String, Map<String, UUID>> patientOwnerByIssuer = new HashMap<>();
    private Map<String, Map<String, UUID>> requestByAccessionIssuer = new HashMap<>();

    public Map<String, Map<String, UUID>> getPatientOwnerByIssuer() { return patientOwnerByIssuer; }
    public void setPatientOwnerByIssuer(Map<String, Map<String, UUID>> patientOwnerByIssuer) {
        this.patientOwnerByIssuer = patientOwnerByIssuer == null ? new HashMap<>() : patientOwnerByIssuer;
    }
    public Map<String, Map<String, UUID>> getRequestByAccessionIssuer() { return requestByAccessionIssuer; }
    public void setRequestByAccessionIssuer(Map<String, Map<String, UUID>> requestByAccessionIssuer) {
        this.requestByAccessionIssuer = requestByAccessionIssuer == null
                ? new HashMap<>() : requestByAccessionIssuer;
    }
}
