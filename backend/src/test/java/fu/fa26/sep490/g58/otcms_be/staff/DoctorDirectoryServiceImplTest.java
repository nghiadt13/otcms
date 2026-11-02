package fu.fa26.sep490.g58.otcms_be.staff;

import fu.fa26.sep490.g58.otcms_be.staff.exception.DoctorProfileException;
import fu.fa26.sep490.g58.otcms_be.staff.mapper.DoctorProfileMapper;
import fu.fa26.sep490.g58.otcms_be.staff.model.DoctorProfile;
import fu.fa26.sep490.g58.otcms_be.staff.repository.DoctorProfileRepository;
import fu.fa26.sep490.g58.otcms_be.staff.service.impl.DoctorDirectoryServiceImpl;
import fu.fa26.sep490.g58.otcms_be.appointment.service.*;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DoctorDirectoryServiceImplTest {
    final DoctorProfileRepository profiles = mock(DoctorProfileRepository.class);
    final GetClinicServiceCatalogService catalog = mock(GetClinicServiceCatalogService.class);
    final ClinicServiceBookingQueryService booking = mock(ClinicServiceBookingQueryService.class);
    final GetAppointmentAvailabilityService availability = mock(GetAppointmentAvailabilityService.class);
    final Clock clock = Clock.fixed(Instant.parse("2026-11-01T18:30:00Z"), ZoneOffset.UTC);
    final DoctorDirectoryServiceImpl directory = new DoctorDirectoryServiceImpl(profiles, catalog, booking, availability, clock);
    final UUID id = UUID.randomUUID();
    final LocalDate today = LocalDate.of(2026, 11, 2);
    DoctorProfile profile(boolean visible, boolean accepting, boolean active) {
        return new DoctorProfile(id, UUID.randomUUID(), "Doctor", null, "private", "Orthopedics", "Qualifications",
                "Biography", "Consultation", visible, accepting, active);
    }
    void found(boolean accepting) {
        when(profiles.findById(id)).thenReturn(Optional.of(DoctorProfileMapper.toEntity(profile(true, accepting, true))));
    }
    ClinicService service() {
        var service = mock(ClinicService.class); when(service.id()).thenReturn(UUID.randomUUID()); return service;
    }
    AppointmentAvailabilitySlot slot(UUID service, Instant start) {
        return new AppointmentAvailabilitySlot(service, id, "Doctor", start, start.plusSeconds(1800), start.plusSeconds(2100));
    }
    @Test void orAcrossAssignedServicesUsesOneWholeRangePerServiceAndVietnamDates() {
        found(true);
        var first = service(); var second = service(); var unassigned = service();
        when(catalog.publicCatalog()).thenReturn(List.of(first, second, unassigned));
        when(booking.hasActiveDoctorAssignment(first.id(), id)).thenReturn(true);
        when(booking.hasActiveDoctorAssignment(second.id(), id)).thenReturn(true);
        when(availability.find(any())).thenAnswer(call -> {
            AppointmentAvailabilityQuery query = call.getArgument(0);
            Instant start = query.serviceId().equals(first.id()) ? Instant.parse("2026-11-01T18:45:00Z")
                    : Instant.parse("2026-11-02T18:45:00Z");
            return List.of(slot(query.serviceId(), start));
        });
        var detail = directory.findPublicDetail(id).orElseThrow();
        assertEquals(today.datesUntil(today.plusDays(7)).toList(), detail.availability().stream().map(day -> day.date()).toList());
        assertEquals(List.of(true, true, false, false, false, false, false), detail.availability().stream().map(day -> day.hasOpenTime()).toList());
        var calls = ArgumentCaptor.forClass(AppointmentAvailabilityQuery.class);
        verify(availability, times(2)).find(calls.capture());
        assertEquals(Set.of(first.id(), second.id()), calls.getAllValues().stream().map(AppointmentAvailabilityQuery::serviceId)
                .collect(java.util.stream.Collectors.toSet()));
        for (var query : calls.getAllValues()) {
            assertEquals(id, query.doctorProfileId()); assertEquals(AppointmentAvailabilityQuery.Audience.PUBLIC, query.audience());
            assertEquals(today, query.fromDate()); assertEquals(today.plusDays(6), query.toDate());
        }
    }
    @Test void acceptingDoctorWithNoSlotsHasSevenFalseDays() {
        found(true); var service = service(); when(catalog.publicCatalog()).thenReturn(List.of(service));
        when(booking.hasActiveDoctorAssignment(service.id(), id)).thenReturn(true);
        when(availability.find(any())).thenReturn(List.of());
        var days = directory.findPublicDetail(id).orElseThrow().availability();
        assertEquals(7, days.size()); assertTrue(days.stream().noneMatch(day -> day.hasOpenTime()));
        verify(availability).find(any());
    }
    @Test void nonAcceptingDoctorNeverCallsCatalogAssignmentOrAvailability() {
        found(false);
        var days = directory.findPublicDetail(id).orElseThrow().availability();
        assertEquals(7, days.size()); assertEquals(today, days.getFirst().date());
        assertTrue(days.stream().noneMatch(day -> day.hasOpenTime()));
        verifyNoInteractions(catalog, booking, availability);
    }
    @Test void hiddenInactiveAndMissingProfilesRemainUnavailableWithoutEnrichment() {
        when(profiles.findById(id)).thenReturn(Optional.empty()); assertTrue(directory.findPublicDetail(id).isEmpty());
        when(profiles.findById(id)).thenReturn(Optional.of(DoctorProfileMapper.toEntity(profile(false, true, true))));
        assertTrue(directory.findPublicDetail(id).isEmpty());
        when(profiles.findById(id)).thenReturn(Optional.of(DoctorProfileMapper.toEntity(profile(true, true, false))));
        assertTrue(directory.findPublicDetail(id).isEmpty());
        verifyNoInteractions(catalog, booking, availability);
    }
    @ParameterizedTest @ValueSource(strings = {"catalog", "assignment", "availability"})
    void dependencyFailureIsNeverConvertedToFullyBooked(String stage) {
        found(true); var service = service();
        UUID serviceId = service.id();
        when(catalog.publicCatalog()).thenReturn(List.of(service));
        when(booking.hasActiveDoctorAssignment(service.id(), id)).thenReturn(true);
        RuntimeException cause = new IllegalStateException("private dependency detail");
        switch (stage) {
            case "catalog" -> doThrow(cause).when(catalog).publicCatalog();
            case "assignment" -> doThrow(cause).when(booking).hasActiveDoctorAssignment(serviceId, id);
            default -> doThrow(cause).when(availability).find(any());
        }
        var failure = assertThrows(DoctorProfileException.class, () -> directory.findPublicDetail(id));
        assertEquals("DEPENDENCY_UNAVAILABLE", failure.getMessage()); assertSame(cause, failure.getCause());
    }
    @Test void listAndExistingProfileQueryNeverEnrichAvailability() {
        var entity = DoctorProfileMapper.toEntity(profile(true, true, true));
        when(profiles.findPublicDirectory()).thenReturn(List.of(entity));
        when(profiles.findById(id)).thenReturn(Optional.of(entity));
        assertEquals(1, directory.listPublic().size()); assertTrue(directory.findPublic(id).isPresent());
        verifyNoInteractions(catalog, booking, availability);
    }
}
