package fu.fa26.sep490.g58.otcms_be.review.service;

import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory.ProfileRecord;
import java.util.UUID;

public interface OwnPatientProfileService { ProfileRecord requireOwnProfile(UUID accountId); }
