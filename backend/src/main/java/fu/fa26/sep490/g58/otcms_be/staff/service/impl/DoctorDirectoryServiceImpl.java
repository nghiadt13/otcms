package fu.fa26.sep490.g58.otcms_be.staff.service.impl;

import fu.fa26.sep490.g58.otcms_be.staff.mapper.DoctorProfileMapper;
import fu.fa26.sep490.g58.otcms_be.staff.model.DoctorProfile;
import fu.fa26.sep490.g58.otcms_be.staff.model.DoctorPublicDetail;
import fu.fa26.sep490.g58.otcms_be.staff.exception.DoctorProfileException;
import fu.fa26.sep490.g58.otcms_be.appointment.service.GetAppointmentAvailabilityService;
import fu.fa26.sep490.g58.otcms_be.appointment.service.AppointmentAvailabilityQuery;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.GetClinicServiceCatalogService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.ClinicServiceBookingQueryService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import fu.fa26.sep490.g58.otcms_be.staff.repository.DoctorProfileRepository;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorDirectoryService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DoctorDirectoryServiceImpl implements DoctorDirectoryService {
    private final DoctorProfileRepository profiles;
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final GetClinicServiceCatalogService catalog;
    private final ClinicServiceBookingQueryService booking;
    private final GetAppointmentAvailabilityService availability;
    private final Clock clock;

    public DoctorDirectoryServiceImpl(DoctorProfileRepository profiles, GetClinicServiceCatalogService catalog,
            ClinicServiceBookingQueryService booking, GetAppointmentAvailabilityService availability, Clock clock) {
        this.profiles = profiles;
        this.catalog = catalog;
        this.booking = booking;
        this.availability = availability;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<DoctorProfile> listPublic() {
        return profiles.findPublicDirectory().stream().map(DoctorProfileMapper::toModel).toList();
    }

    @Transactional(readOnly = true)
    public Optional<DoctorProfile> findPublic(UUID id) {
        return profiles.findById(id).filter(profile -> profile.publiclyVisible() && profile.inActiveDirectory())
                .map(DoctorProfileMapper::toModel);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DoctorPublicDetail> findPublicDetail(UUID id) {
        return findPublic(id).map(profile -> {
            LocalDate today = LocalDate.now(clock.withZone(CLINIC_ZONE));
            LocalDate last = today.plusDays(6);
            var openDates = new HashSet<LocalDate>();
            if (profile.acceptingNewBookings()) {
                try {
                    for (var service : catalog.publicCatalog()) {
                        if (!booking.hasActiveDoctorAssignment(service.id(), profile.id())) continue;
                        var query = new AppointmentAvailabilityQuery(service.id(), today, last, profile.id(),
                                AppointmentAvailabilityQuery.Audience.PUBLIC);
                        for (var slot : availability.find(query)) {
                            if (!profile.id().equals(slot.doctorId())) continue;
                            LocalDate date = slot.startTime().atZone(CLINIC_ZONE).toLocalDate();
                            if (!date.isBefore(today) && !date.isAfter(last)) openDates.add(date);
                        }
                    }
                } catch (RuntimeException failure) {
                    // A failed dependency is not evidence that all seven dates are fully booked.
                    var unavailable = new DoctorProfileException("DEPENDENCY_UNAVAILABLE");
                    unavailable.initCause(failure);
                    throw unavailable;
                }
            }
            var days = today.datesUntil(last.plusDays(1))
                    .map(date -> new DoctorPublicDetail.AvailabilityDay(date, openDates.contains(date))).toList();
            return new DoctorPublicDetail(profile, days);
        });
    }
}
