package fu.fa26.sep490.g58.otcms_be.staff.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import fu.fa26.sep490.g58.otcms_be.staff.exception.DoctorProfileException;
import fu.fa26.sep490.g58.otcms_be.staff.model.DoctorWorkShift;
import fu.fa26.sep490.g58.otcms_be.staff.model.entity.DoctorWorkShiftJpaEntity;
import fu.fa26.sep490.g58.otcms_be.staff.model.entity.StaffProfileJpaEntity;
import fu.fa26.sep490.g58.otcms_be.staff.repository.DoctorWorkShiftRepository;
import fu.fa26.sep490.g58.otcms_be.staff.repository.StaffProfileRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DoctorScheduleServiceImplTest {
    private static final Instant FROM = Instant.parse("2026-11-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-11-08T00:00:00Z");

    private final StaffProfileRepository profiles = mock(StaffProfileRepository.class);
    private final DoctorWorkShiftRepository shifts = mock(DoctorWorkShiftRepository.class);
    private final DoctorScheduleServiceImpl service = new DoctorScheduleServiceImpl(profiles, shifts);

    @Test
    void forAccountReturnsShiftsOfDoctorProfileLinkedToAccount() {
        UUID accountId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID shiftId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        StaffProfileJpaEntity doctor = new StaffProfileJpaEntity(profileId, accountId, "Dr. An", "0912345678");
        doctor.becomeDoctor("Orthopedics", "MD", "Bio", "Mon-Fri", true);
        Instant start = Instant.parse("2026-11-02T02:00:00Z");
        Instant end = Instant.parse("2026-11-02T05:00:00Z");
        when(profiles.findByAccountIdAndDoctorTrue(accountId)).thenReturn(Optional.of(doctor));
        when(profiles.existsByIdAndDoctorTrue(profileId)).thenReturn(true);
        when(shifts.findInRange(profileId, FROM, TO))
                .thenReturn(List.of(new DoctorWorkShiftJpaEntity(shiftId, profileId, start, end, roomId)));

        List<DoctorWorkShift> result = service.forAccount(accountId, FROM, TO);

        assertEquals(1, result.size());
        assertEquals(shiftId, result.get(0).id());
        assertEquals(profileId, result.get(0).doctorProfileId());
        assertEquals(start, result.get(0).startsAt());
        assertEquals(end, result.get(0).endsAt());
        assertEquals(roomId, result.get(0).roomId());
    }

    @Test
    void forAccountRejectsMissingNonDoctorOrNullAccountBeforeQueryingShifts() {
        UUID accountId = UUID.randomUUID();
        when(profiles.findByAccountIdAndDoctorTrue(accountId)).thenReturn(Optional.empty());

        assertEquals("DOCTOR_PROFILE_NOT_FOUND", assertThrows(DoctorProfileException.class,
                () -> service.forAccount(accountId, FROM, TO)).getMessage());
        assertEquals("DOCTOR_PROFILE_NOT_FOUND", assertThrows(DoctorProfileException.class,
                () -> service.forAccount(null, FROM, TO)).getMessage());
        verify(profiles, never()).findByAccountIdAndDoctorTrue(null);
        verifyNoInteractions(shifts);
    }

    @Test
    void forProfileRejectsNullEqualOrInvertedRangeBeforeAnyLookup() {
        UUID profileId = UUID.randomUUID();
        assertEquals("DOCTOR_SCHEDULE_RANGE_INVALID", assertThrows(DoctorProfileException.class,
                () -> service.forProfile(profileId, null, TO)).getMessage());
        assertEquals("DOCTOR_SCHEDULE_RANGE_INVALID", assertThrows(DoctorProfileException.class,
                () -> service.forProfile(profileId, FROM, null)).getMessage());
        assertEquals("DOCTOR_SCHEDULE_RANGE_INVALID", assertThrows(DoctorProfileException.class,
                () -> service.forProfile(profileId, FROM, FROM)).getMessage());
        assertEquals("DOCTOR_SCHEDULE_RANGE_INVALID", assertThrows(DoctorProfileException.class,
                () -> service.forProfile(profileId, TO, FROM)).getMessage());
        verifyNoInteractions(profiles, shifts);
    }

    @Test
    void forProfileAcceptsExactNinetyDayRangeAndMapsMultipleShifts() {
        UUID profileId = UUID.randomUUID();
        Instant maxTo = FROM.plus(Duration.ofDays(90));
        UUID shift1 = UUID.randomUUID();
        UUID shift2 = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        when(profiles.existsByIdAndDoctorTrue(profileId)).thenReturn(true);
        when(shifts.findInRange(profileId, FROM, maxTo)).thenReturn(List.of(
                new DoctorWorkShiftJpaEntity(shift1, profileId, FROM.plusSeconds(3600), FROM.plusSeconds(7200), roomId),
                new DoctorWorkShiftJpaEntity(shift2, profileId, maxTo.minusSeconds(7200), maxTo.minusSeconds(3600))));

        List<DoctorWorkShift> result = service.forProfile(profileId, FROM, maxTo);

        assertEquals(2, result.size());
        assertEquals(roomId, result.get(0).roomId());
        assertNull(result.get(1).roomId());
    }

    @Test
    void forProfileRejectsRangeExceedingNinetyDays() {
        UUID profileId = UUID.randomUUID();
        Instant overMax = FROM.plus(Duration.ofDays(90)).plusSeconds(1);

        assertEquals("DOCTOR_SCHEDULE_RANGE_INVALID", assertThrows(DoctorProfileException.class,
                () -> service.forProfile(profileId, FROM, overMax)).getMessage());
        verifyNoInteractions(profiles, shifts);
    }

    @Test
    void forProfileRejectsUnknownNonDoctorOrNullProfileBeforeReadingShifts() {
        UUID profileId = UUID.randomUUID();
        when(profiles.existsByIdAndDoctorTrue(profileId)).thenReturn(false);

        assertEquals("DOCTOR_PROFILE_NOT_FOUND", assertThrows(DoctorProfileException.class,
                () -> service.forProfile(profileId, FROM, TO)).getMessage());
        assertEquals("DOCTOR_PROFILE_NOT_FOUND", assertThrows(DoctorProfileException.class,
                () -> service.forProfile(null, FROM, TO)).getMessage());
        verify(profiles, never()).existsByIdAndDoctorTrue(null);
        verify(shifts, never()).findInRange(any(), any(), any());
    }
}
