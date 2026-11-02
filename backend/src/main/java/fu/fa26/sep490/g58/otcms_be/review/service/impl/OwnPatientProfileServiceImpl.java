package fu.fa26.sep490.g58.otcms_be.review.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory;
import fu.fa26.sep490.g58.otcms_be.patient.service.AuthorizePatientProfileService;
import fu.fa26.sep490.g58.otcms_be.review.exception.ReviewOperationException;
import fu.fa26.sep490.g58.otcms_be.review.service.OwnPatientProfileService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly = true)
public class OwnPatientProfileServiceImpl implements OwnPatientProfileService {
    private final PatientProfileDirectory profiles;
    private final AuthorizePatientProfileService authorization;
    public OwnPatientProfileServiceImpl(PatientProfileDirectory profiles, AuthorizePatientProfileService authorization) {
        this.profiles = profiles; this.authorization = authorization;
    }
    public PatientProfileDirectory.ProfileRecord requireOwnProfile(UUID accountId) {
        var id = profiles.findPatientProfile(accountId).orElseThrow(() -> new ReviewOperationException(403, "MSG-56"));
        try { authorization.authorize(accountId, id); }
        catch (fu.fa26.sep490.g58.otcms_be.patient.exception.PatientAuthorizationDeniedException denied) {
            throw new ReviewOperationException(403, "MSG-56");
        }
        return profiles.findProfile(id).orElseThrow(() -> new ReviewOperationException(403, "MSG-56"));
    }
}
