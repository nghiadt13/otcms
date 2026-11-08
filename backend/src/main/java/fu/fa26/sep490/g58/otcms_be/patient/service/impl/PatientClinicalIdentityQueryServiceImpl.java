package fu.fa26.sep490.g58.otcms_be.patient.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory;
import fu.fa26.sep490.g58.otcms_be.patient.service.PatientClinicalIdentityQueryService;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PatientClinicalIdentityQueryServiceImpl implements PatientClinicalIdentityQueryService {
    private final PatientProfileDirectory profiles;

    public PatientClinicalIdentityQueryServiceImpl(PatientProfileDirectory profiles) {
        this.profiles = profiles;
    }

    @Override
    public Map<UUID, ClinicalIdentity> findClinicalIdentities(Collection<UUID> patientProfileIds) {
        if (patientProfileIds == null || patientProfileIds.isEmpty()) return Map.of();
        var ids = new LinkedHashSet<>(patientProfileIds);
        if (ids.contains(null)) throw new IllegalArgumentException("Patient profile IDs must not be null");
        var result = new LinkedHashMap<UUID, ClinicalIdentity>();
        for (var profile : profiles.findProfiles(ids)) {
            if (ids.contains(profile.id())) result.put(profile.id(),
                    new ClinicalIdentity(profile.id(), profile.fullName(), profile.dateOfBirth(), profile.gender()));
        }
        return Map.copyOf(result);
    }
}
