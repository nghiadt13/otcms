package fu.fa26.sep490.g58.otcms_be.staff.mapper;

import fu.fa26.sep490.g58.otcms_be.staff.model.DoctorProfile;
import fu.fa26.sep490.g58.otcms_be.staff.model.DoctorWorkShift;
import fu.fa26.sep490.g58.otcms_be.staff.model.dto.response.DoctorAdminResponse;
import fu.fa26.sep490.g58.otcms_be.staff.model.dto.response.DoctorItems;
import fu.fa26.sep490.g58.otcms_be.staff.model.dto.response.DoctorPublicResponse;
import fu.fa26.sep490.g58.otcms_be.staff.model.dto.response.ShiftItems;
import fu.fa26.sep490.g58.otcms_be.staff.model.dto.response.ShiftResponse;
import fu.fa26.sep490.g58.otcms_be.staff.model.entity.DoctorProfileJpaEntity;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DoctorProfileMapper {
    public static DoctorProfile toModel(DoctorProfileJpaEntity entity) {
        return new DoctorProfile(entity.id(), entity.accountId(), entity.fullName(), entity.photoUrl(),
                entity.workContact(), entity.specialties(), entity.qualifications(), entity.biography(),
                entity.consultationInformation(), entity.publiclyVisible(), entity.acceptingNewBookings(),
                entity.inActiveDirectory());
    }

    public static DoctorProfileJpaEntity toEntity(DoctorProfile profile) {
        return new DoctorProfileJpaEntity(profile.id(), profile.accountId(), profile.fullName(), profile.photoUrl(),
                profile.workContact(), profile.specialties(), profile.qualifications(), profile.biography(),
                profile.consultationInformation(), profile.publiclyVisible(), profile.acceptingNewBookings(),
                profile.inActiveDirectory());
    }

    public DoctorPublicResponse toPublicResponse(DoctorProfile p) { return new DoctorPublicResponse(p.id(), p.fullName(), p.photoUrl(), p.specialties(), p.qualifications(), p.biography(), p.consultationInformation()); }
    public DoctorAdminResponse toAdminResponse(DoctorProfile p) { return new DoctorAdminResponse(p.id(), p.accountId(), p.fullName(), p.photoUrl(), p.workContact(), p.specialties(), p.qualifications(), p.biography(), p.consultationInformation(), p.publiclyVisible(), p.acceptingNewBookings(), p.inActiveDirectory()); }
    public DoctorItems toItems(List<DoctorProfile> profiles) { return new DoctorItems(profiles.stream().map(this::toPublicResponse).toList()); }
    public ShiftItems toShiftItems(List<DoctorWorkShift> shifts) { return new ShiftItems(shifts.stream().map(s -> new ShiftResponse(s.id(), s.doctorProfileId(), s.startsAt(), s.endsAt(), s.roomId())).toList()); }
}
