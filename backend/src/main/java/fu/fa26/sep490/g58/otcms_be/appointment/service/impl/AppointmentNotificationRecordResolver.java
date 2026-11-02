package fu.fa26.sep490.g58.otcms_be.appointment.service.impl;

import fu.fa26.sep490.g58.otcms_be.appointment.repository.AppointmentJpaRepository;
import fu.fa26.sep490.g58.otcms_be.auth.service.*;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorProfileIdentityQueryService;
import fu.fa26.sep490.g58.otcms_be.common.security.*;
import fu.fa26.sep490.g58.otcms_be.notification.model.NotificationRelatedKind;
import fu.fa26.sep490.g58.otcms_be.notification.service.NotificationRelatedRecordResolver;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AppointmentNotificationRecordResolver implements NotificationRelatedRecordResolver {
    private final AppointmentJpaRepository records;
    private final PatientProfileDirectory patients;
    private final PatientIdentityDirectory identities;
    private final ReceptionistAccountAccessService receptionists;
    private final DoctorAccountAccessService doctors;
    private final DoctorProfileIdentityQueryService doctorProfiles;
    public AppointmentNotificationRecordResolver(AppointmentJpaRepository records, PatientProfileDirectory patients,
            PatientIdentityDirectory identities, ReceptionistAccountAccessService receptionists,
            DoctorAccountAccessService doctors, DoctorProfileIdentityQueryService doctorProfiles) {
        this.records = records; this.patients = patients; this.identities = identities;
        this.receptionists = receptionists; this.doctors = doctors; this.doctorProfiles = doctorProfiles;
    }
    public NotificationRelatedKind kind() { return NotificationRelatedKind.APPOINTMENT; }
    public boolean available(AuthenticatedAccount actor, UUID id) {
        var record = records.findById(id);
        if (record.isEmpty()) return false;
        return switch (actor.accountRole()) {
            case ADMIN -> true;
            case PATIENT -> identities.isActivePatient(actor.accountId()) && patients.findPatientProfile(actor.accountId())
                    .filter(profile -> profile.equals(record.get().getPatientProfileId())).isPresent();
            case RECEPTIONIST -> receptionists.isActiveReceptionistAccount(actor.accountId());
            case DOCTOR -> doctors.isActiveDoctorAccount(actor.accountId()) && doctorProfiles.findDoctorProfileId(actor.accountId())
                    .filter(profile -> profile.equals(record.get().getDoctorProfileId())).isPresent();
            default -> false;
        };
    }
}
