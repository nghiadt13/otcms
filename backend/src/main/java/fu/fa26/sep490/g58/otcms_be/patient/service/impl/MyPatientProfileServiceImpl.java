package fu.fa26.sep490.g58.otcms_be.patient.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.service.PatientIdentityDirectory;
import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory;
import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory.ProfileRecord;
import fu.fa26.sep490.g58.otcms_be.common.api.ErrorResponse.FieldError;
import fu.fa26.sep490.g58.otcms_be.common.security.*;
import fu.fa26.sep490.g58.otcms_be.patient.exception.*;
import static fu.fa26.sep490.g58.otcms_be.patient.exception.MyPatientProfileException.Reason.*;
import fu.fa26.sep490.g58.otcms_be.patient.model.PatientProfile;
import fu.fa26.sep490.g58.otcms_be.patient.model.dto.request.UpdatePatientProfileRequest;
import fu.fa26.sep490.g58.otcms_be.patient.service.MyPatientProfileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MyPatientProfileServiceImpl implements MyPatientProfileService {
    private final PatientIdentityDirectory identities;
    private final PatientProfileDirectory profiles;
    public MyPatientProfileServiceImpl(PatientIdentityDirectory identities, PatientProfileDirectory profiles) {
        this.identities = identities; this.profiles = profiles;
    }

    @Override @Transactional(readOnly = true)
    public PatientProfile get(AuthenticatedAccount actor) { return model(own(actor)); }

    @Override @Transactional
    public PatientProfile update(AuthenticatedAccount actor, UpdatePatientProfileRequest r) {
        var existing = own(actor);
        if (r == null || r.version() == null || r.version() < 0) throw new MyPatientProfileException(INVALID_INPUT);
        if (r.version() != existing.version()) throw new MyPatientProfileException(STALE_VERSION);
        ProfileRecord validated;
        try {
            validated = AdministrativeProfileFields.record(existing.id(), existing.accountId(), r.fullName(), r.phone(),
                    r.phoneOwnerType(), r.dateOfBirth(), r.gender(), r.address(), r.nationalId(),
                    r.identityDocumentType(), r.version(), false, false);
        } catch (PatientProfileException invalid) {
            throw new MyPatientProfileException(INVALID_INPUT, invalid.fieldErrors().stream()
                    .map(f -> new FieldError(f.field(), "MSG-08")).toList());
        }
        return model(profiles.update(validated, r.version()));
    }

    private ProfileRecord own(AuthenticatedAccount actor) {
        if (actor == null || actor.accountRole() != AccountRole.PATIENT || !identities.isActivePatient(actor.accountId()))
            throw new MyPatientProfileException(ACCESS_DENIED);
        var id = profiles.findPatientProfile(actor.accountId()).orElseThrow(() -> new MyPatientProfileException(NOT_FOUND));
        var record = profiles.findProfile(id).orElseThrow(() -> new MyPatientProfileException(NOT_FOUND));
        if (!actor.accountId().equals(record.accountId())) throw new MyPatientProfileException(ACCESS_DENIED);
        return record;
    }

    private PatientProfile model(ProfileRecord p) {
        return new PatientProfile(p.id(), p.fullName(), p.phoneCanonical(), p.dateOfBirth(), p.gender(),
                p.phoneOwnerType(), p.address(), p.nationalId(), p.identityDocumentType(), p.accountId() != null, p.version());
    }
}
