package fu.fa26.sep490.g58.otcms_be.patient.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.service.*;
import fu.fa26.sep490.g58.otcms_be.common.api.ErrorResponse.FieldError;
import fu.fa26.sep490.g58.otcms_be.common.security.*;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.patient.exception.MedicalHistoryException;
import fu.fa26.sep490.g58.otcms_be.patient.model.dto.MedicalHistoryDtos.*;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.PatientProfileJpaEntity;
import fu.fa26.sep490.g58.otcms_be.patient.repository.PatientMedicalHistoryRepository;
import fu.fa26.sep490.g58.otcms_be.patient.service.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PatientMedicalHistoryServiceImpl implements PatientMedicalHistoryService {
    private final PatientIdentityDirectory patients;
    private final DoctorAccountAccessService doctors;
    private final DoctorCareRelationPort doctorRelations;
    private final PatientProfileAccessService access;
    private final PatientProfileDirectory profiles;
    private final MyAccountQueryService accounts;
    private final PatientMedicalHistoryRepository histories;
    private final CoordinationGuardService guard;
    private final Clock clock;

    public PatientMedicalHistoryServiceImpl(PatientIdentityDirectory patients, DoctorAccountAccessService doctors,
            DoctorCareRelationPort doctorRelations, PatientProfileAccessService access, PatientProfileDirectory profiles,
            MyAccountQueryService accounts, PatientMedicalHistoryRepository histories, CoordinationGuardService guard, Clock clock) {
        this.patients = patients; this.doctors = doctors; this.doctorRelations = doctorRelations;
        this.access = access; this.profiles = profiles; this.accounts = accounts;
        this.histories = histories; this.guard = guard; this.clock = clock;
    }

    @Override public MedicalHistoryResponse get(AuthenticatedAccount actor, UUID profileId) {
        requireActor(actor);
        authorizeTarget(actor, profileId);
        return read(profileId);
    }

    @Override @Transactional
    public MedicalHistoryResponse update(AuthenticatedAccount actor, UUID profileId, UpdateMedicalHistoryRequest request) {
        requireActor(actor);
        // Share KAN-26's revoke coordination; only local DB work occurs while held.
        guard.acquire();
        authorizeTarget(actor, profileId);
        if (request == null) throw invalid(List.of());
        List<FieldError> errors = new ArrayList<>();
        String conditions = text(request.conditions(), "conditions", errors);
        String allergies = text(request.allergies(), "allergies", errors);
        String procedures = text(request.procedures(), "procedures", errors);
        String injuries = text(request.injuries(), "injuries", errors);
        String orthopedic = text(request.orthopedicHistory(), "orthopedicHistory", errors);
        if (request.version() == null || request.version() < 0) errors.add(new FieldError("version", "MSG-20"));
        if (!errors.isEmpty()) throw invalid(errors);
        Instant now = clock.instant();
        int written = request.version() == 0
                ? histories.insertInitial(profileId, conditions, allergies, procedures, injuries, orthopedic, now, actor.accountId())
                : histories.updateIfVersion(profileId, conditions, allergies, procedures, injuries, orthopedic, now, actor.accountId(), request.version());
        if (written != 1) throw new MedicalHistoryException("STALE_VERSION");
        return read(profileId);
    }

    private void requireActor(AuthenticatedAccount actor) {
        if (actor == null || actor.accountId() == null || actor.accountRole() == null) throw denied();
        boolean active = switch (actor.accountRole()) {
            case PATIENT -> patients.isActivePatient(actor.accountId());
            case DOCTOR -> doctors.isActiveDoctorAccount(actor.accountId());
            default -> false;
        };
        if (!active) throw denied();
    }
    private void authorizeTarget(AuthenticatedAccount actor, UUID profileId) {
        if (profileId == null) throw missing();
        if (actor.accountRole() == AccountRole.PATIENT) {
            // Do not resolve existence before the KAN-26 helper authorizes this target.
            if (!access.canAccess(actor.accountId(), profileId)) throw missing();
        } else if (!doctorRelations.isResponsibleDoctor(actor.accountId(), profileId)) throw denied();
        if (profiles.findProfile(profileId).isEmpty()) throw missing();
    }
    private MedicalHistoryResponse read(UUID profileId) {
        return histories.findById(profileId).filter(p -> p.getMedicalHistoryUpdatedAt() != null).map(this::response).orElseGet(MedicalHistoryResponse::empty);
    }
    private MedicalHistoryResponse response(PatientProfileJpaEntity row) {
        String name = accounts.find(row.getMedicalHistoryUpdatedBy()).map(MyAccountQueryService.MyAccountView::displayName)
                .filter(value -> value != null && !value.isBlank()).orElse(null);
        return new MedicalHistoryResponse(row.getConditions(), row.getAllergies(), row.getProcedures(), row.getInjuries(),
                row.getOrthopedicHistory(), row.getMedicalHistoryUpdatedAt().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toOffsetDateTime(), name, row.getMedicalHistoryVersion());
    }
    private String text(String value, String field, List<FieldError> errors) {
        if (value == null) { errors.add(new FieldError(field, "MSG-20")); return ""; }
        String trimmed = value.trim();
        if (trimmed.length() > 2000) errors.add(new FieldError(field, "MSG-20"));
        return trimmed;
    }
    private static MedicalHistoryException invalid(List<FieldError> fields) { return new MedicalHistoryException("VALIDATION_FAILED", fields); }
    private static MedicalHistoryException missing() { return new MedicalHistoryException("PATIENT_PROFILE_NOT_FOUND"); }
    private static MedicalHistoryException denied() { return new MedicalHistoryException("ACCESS_DENIED"); }
}
