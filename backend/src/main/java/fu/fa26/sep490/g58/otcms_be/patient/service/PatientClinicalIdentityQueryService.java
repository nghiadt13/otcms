package fu.fa26.sep490.g58.otcms_be.patient.service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Internal minimal identity boundary. Callers must establish care access before choosing IDs. */
public interface PatientClinicalIdentityQueryService {
    /** Returns known identities only; an absent requested ID is omitted. Never supplies administrative fields. */
    Map<UUID, ClinicalIdentity> findClinicalIdentities(Collection<UUID> patientProfileIds);

    record ClinicalIdentity(UUID patientProfileId, String fullName, LocalDate dateOfBirth, String gender) { }
}
