package fu.fa26.sep490.g58.otcms_be.patient.service;

import java.util.UUID;

public interface AuthorizePatientProfileService {
    AuthorizedPatientProfile authorize(UUID accountId, UUID targetPatientProfileId);

    class AuthorizedPatientProfile {
        private final UUID patientProfileId;
        private final AuthorizationScope scope;

        public AuthorizedPatientProfile(UUID patientProfileId, AuthorizationScope scope) {
            this.patientProfileId = patientProfileId;
            this.scope = scope;
        }

        public UUID patientProfileId() { return patientProfileId; }
        public AuthorizationScope scope() { return scope; }
    }

    enum AuthorizationScope { SELF }
}
