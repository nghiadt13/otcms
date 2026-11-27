package fu.fa26.sep490.g58.otcms_be.staff.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import fu.fa26.sep490.g58.otcms_be.appointment.service.AppointmentMutationImpactService;
import fu.fa26.sep490.g58.otcms_be.appointment.service.AppointmentMutationImpactService.OccupiedBookingInterval;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.auth.service.StaffAccountDirectory;
import fu.fa26.sep490.g58.otcms_be.auth.service.StaffAccountDirectory.Entry;
import fu.fa26.sep490.g58.otcms_be.common.storage.ObjectStoragePort;
import fu.fa26.sep490.g58.otcms_be.staff.exception.StaffProfileException;
import fu.fa26.sep490.g58.otcms_be.staff.model.dto.request.StaffProfileInput;
import fu.fa26.sep490.g58.otcms_be.staff.model.dto.response.StaffProfileResponse;
import fu.fa26.sep490.g58.otcms_be.staff.model.dto.response.StaffProfileResponse.DoctorInfo;
import fu.fa26.sep490.g58.otcms_be.staff.model.dto.response.StaffProfilesPageResponse;
import fu.fa26.sep490.g58.otcms_be.staff.model.entity.StaffProfileJpaEntity;
import fu.fa26.sep490.g58.otcms_be.staff.repository.StaffProfileRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class StaffProfileAdminServiceTest {
    private final StaffProfileRepository profiles = mock(StaffProfileRepository.class);
    private final StaffAccountDirectory accounts = mock(StaffAccountDirectory.class);
    private final AppointmentMutationImpactService appointments = mock(AppointmentMutationImpactService.class);
    private final ObjectStoragePort storage = mock(ObjectStoragePort.class);
    private final StaffProfileAdminService service =
            new StaffProfileAdminService(profiles, accounts, appointments, storage);

    private static final String PHONE = "0912345678";
    private static final String EMAIL = "staff@example.test";
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static StaffProfileJpaEntity staffProfile(UUID id, UUID accountId, String name) {
        return new StaffProfileJpaEntity(id, accountId, name, EMAIL);
    }

    private static StaffProfileJpaEntity doctorProfile(UUID id, UUID accountId, String name, boolean publiclyVisible) {
        StaffProfileJpaEntity profile = staffProfile(id, accountId, name);
        profile.becomeDoctor("Orthopedics", "MD", "Bio", "Mon-Fri", publiclyVisible);
        return profile;
    }

    private static StaffProfileInput staffInput(UUID accountId, String name, String contact) {
        return new StaffProfileInput(accountId, name, contact, null);
    }

    private static StaffProfileInput doctorInput(UUID accountId, String name, boolean publiclyVisible) {
        return new StaffProfileInput(accountId, name, PHONE,
                new DoctorInfo(List.of("Orthopedics", "Sports"), "MD", "Bio", "Mon-Fri", publiclyVisible));
    }

    private void stubAccount(UUID accountId, String username, AccountType role) {
        Entry entry = new Entry(accountId, username, username + " full name", role);
        when(accounts.activeStaff(accountId)).thenReturn(Optional.of(entry));
        when(accounts.staff(accountId)).thenReturn(Optional.of(entry));
    }

    @Test
    void listRejectsOutOfRangePagingBeforeQueryingProfiles() {
        assertEquals("VALIDATION_FAILED",
                assertThrows(StaffProfileException.class, () -> service.list(null, null, null, null, -1, 10)).getMessage());
        assertEquals("VALIDATION_FAILED",
                assertThrows(StaffProfileException.class, () -> service.list(null, null, null, null, 0, 0)).getMessage());
        assertEquals("VALIDATION_FAILED",
                assertThrows(StaffProfileException.class, () -> service.list(null, null, null, null, 0, 101)).getMessage());
        verifyNoInteractions(profiles);
    }

    @Test
    void listRejectsOverlongSearchTermOrUnknownStatus() {
        String overlong = "a".repeat(101);
        assertEquals("VALIDATION_FAILED",
                assertThrows(StaffProfileException.class, () -> service.list(overlong, null, null, null, 0, 10)).getMessage());
        assertEquals("VALIDATION_FAILED",
                assertThrows(StaffProfileException.class, () -> service.list(null, null, "DISABLED", null, 0, 10)).getMessage());
        verifyNoInteractions(profiles);
    }

    @Test
    void listSearchMatchesDisplayNameOrUsernameIgnoringCase() {
        UUID zedId = UUID.randomUUID();
        UUID betaId = UUID.randomUUID();
        UUID gammaId = UUID.randomUUID();
        UUID zedAccount = UUID.randomUUID();
        UUID betaAccount = UUID.randomUUID();
        UUID gammaAccount = UUID.randomUUID();
        when(profiles.findAll()).thenReturn(List.of(
                staffProfile(zedId, zedAccount, "Zed Nurse"),
                staffProfile(betaId, betaAccount, "Beta Clerk"),
                staffProfile(gammaId, gammaAccount, "Gamma")));
        stubAccount(zedAccount, "zed.nurse", AccountType.RECEPTIONIST);
        stubAccount(betaAccount, "alpha.user", AccountType.RECEPTIONIST);
        stubAccount(gammaAccount, "gamma", AccountType.RECEPTIONIST);

        StaffProfilesPageResponse byDisplayName = service.list("  ZED ", null, "  ", null, 0, 10);
        StaffProfilesPageResponse byUsername = service.list("ALPHA", null, "", null, 0, 10);

        assertEquals(List.of(zedId), byDisplayName.items().stream().map(StaffProfileResponse::id).toList());
        assertEquals(List.of(betaId), byUsername.items().stream().map(StaffProfileResponse::id).toList());
        assertEquals(1, byUsername.totalItems());
    }

    @Test
    void listSortsByDisplayNameCaseInsensitivelyAndPaginates() {
        UUID gammaId = UUID.randomUUID();
        UUID alphaId = UUID.randomUUID();
        UUID betaId = UUID.randomUUID();
        UUID gammaAccount = UUID.randomUUID();
        UUID alphaAccount = UUID.randomUUID();
        UUID betaAccount = UUID.randomUUID();
        when(profiles.findAll()).thenReturn(List.of(
                staffProfile(gammaId, gammaAccount, "gamma"),
                staffProfile(alphaId, alphaAccount, "Alpha"),
                staffProfile(betaId, betaAccount, "Beta")));
        stubAccount(gammaAccount, "g", AccountType.RECEPTIONIST);
        stubAccount(alphaAccount, "a", AccountType.RECEPTIONIST);
        stubAccount(betaAccount, "b", AccountType.RECEPTIONIST);

        StaffProfilesPageResponse page = service.list(null, null, null, null, 1, 2);

        assertEquals(1, page.page());
        assertEquals(2, page.size());
        assertEquals(3, page.totalItems());
        assertEquals(2, page.totalPages());
        assertEquals(List.of(gammaId), page.items().stream().map(StaffProfileResponse::id).toList());
    }

    @Test
    void listFiltersByRoleVisibilityAndStatus() {
        UUID publicDoctorId = UUID.randomUUID();
        UUID hiddenDoctorId = UUID.randomUUID();
        UUID receptionistId = UUID.randomUUID();
        UUID removedDoctorId = UUID.randomUUID();
        UUID publicAccount = UUID.randomUUID();
        UUID hiddenAccount = UUID.randomUUID();
        UUID receptionistAccount = UUID.randomUUID();
        UUID removedAccount = UUID.randomUUID();
        StaffProfileJpaEntity removedDoctor = doctorProfile(removedDoctorId, removedAccount, "Dr Gone", true);
        removedDoctor.remove();
        when(profiles.findAll()).thenReturn(List.of(
                doctorProfile(publicDoctorId, publicAccount, "Dr Public", true),
                doctorProfile(hiddenDoctorId, hiddenAccount, "Dr Hidden", false),
                staffProfile(receptionistId, receptionistAccount, "Reception"),
                removedDoctor));
        stubAccount(publicAccount, "dr.public", AccountType.DOCTOR);
        stubAccount(hiddenAccount, "dr.hidden", AccountType.DOCTOR);
        stubAccount(receptionistAccount, "reception", AccountType.RECEPTIONIST);
        stubAccount(removedAccount, "dr.gone", AccountType.DOCTOR);

        StaffProfilesPageResponse page = service.list(null, "ORTHOPEDIC_DOCTOR", "ACTIVE", true, 0, 10);

        assertEquals(List.of(publicDoctorId), page.items().stream().map(StaffProfileResponse::id).toList());
    }

    @Test
    void detailReturnsProfileWithAccountUsernameAndRole() {
        UUID id = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        when(profiles.findById(id)).thenReturn(Optional.of(staffProfile(id, accountId, "Jane")));
        stubAccount(accountId, "jane", AccountType.RECEPTIONIST);

        StaffProfileResponse response = service.detail(id);

        assertEquals(id, response.id());
        assertEquals("jane", response.username());
        assertEquals("RECEPTIONIST", response.role());
        assertNull(response.doctor());
    }

    @Test
    void detailThrowsProfileNotFoundForUnknownOrNullId() {
        UUID id = UUID.randomUUID();
        when(profiles.findById(id)).thenReturn(Optional.empty());

        assertEquals("STAFF_PROFILE_NOT_FOUND",
                assertThrows(StaffProfileException.class, () -> service.detail(id)).getMessage());
        assertEquals("STAFF_PROFILE_NOT_FOUND",
                assertThrows(StaffProfileException.class, () -> service.detail(null)).getMessage());
        verify(profiles, never()).findById(null);
    }

    @Test
    void eligibleStaffExcludesAccountsThatAlreadyHaveAProfile() {
        UUID linked = UUID.randomUUID();
        UUID free = UUID.randomUUID();
        Entry linkedEntry = new Entry(linked, "linked", "Linked", AccountType.RECEPTIONIST);
        Entry freeEntry = new Entry(free, "free", "Free", AccountType.LAB_TECHNICIAN);
        when(accounts.activeStaff()).thenReturn(List.of(linkedEntry, freeEntry));
        when(profiles.existsByAccountId(linked)).thenReturn(true);
        when(profiles.existsByAccountId(free)).thenReturn(false);

        assertEquals(List.of(freeEntry), service.eligible());
    }

    @Test
    void createRejectsMissingInputOrAccountBeforeAnyLookup() {
        assertEquals("VALIDATION_FAILED",
                assertThrows(StaffProfileException.class, () -> service.create(null, null, null)).getMessage());
        StaffProfileInput noAccount = new StaffProfileInput(null, "Jane", PHONE, null);
        assertEquals("VALIDATION_FAILED",
                assertThrows(StaffProfileException.class, () -> service.create(noAccount, null, null)).getMessage());
        verifyNoInteractions(accounts, profiles, storage);
    }

    @Test
    void createRejectsAccountThatIsNotAnActiveStaffMember() {
        UUID accountId = UUID.randomUUID();
        when(accounts.activeStaff(accountId)).thenReturn(Optional.empty());

        StaffProfileException ex = assertThrows(StaffProfileException.class,
                () -> service.create(staffInput(accountId, "Jane", PHONE), null, null));

        assertEquals("STAFF_ACCOUNT_UNAVAILABLE", ex.getMessage());
        verify(profiles, never()).saveAndFlush(any(StaffProfileJpaEntity.class));
    }

    @Test
    void createRejectsAccountThatAlreadyHasAStaffProfile() {
        UUID accountId = UUID.randomUUID();
        stubAccount(accountId, "jane", AccountType.RECEPTIONIST);
        when(profiles.existsByAccountId(accountId)).thenReturn(true);

        StaffProfileException ex = assertThrows(StaffProfileException.class,
                () -> service.create(staffInput(accountId, "Jane", PHONE), null, null));

        assertEquals("STAFF_PROFILE_CONFLICT", ex.getMessage());
        verify(profiles, never()).saveAndFlush(any(StaffProfileJpaEntity.class));
    }

    @Test
    void createRejectsDoctorDetailsThatDoNotMatchTheAccountRole() {
        UUID doctorAccount = UUID.randomUUID();
        UUID receptionAccount = UUID.randomUUID();
        stubAccount(doctorAccount, "dr.an", AccountType.DOCTOR);
        stubAccount(receptionAccount, "jane", AccountType.RECEPTIONIST);

        assertEquals("VALIDATION_FAILED", assertThrows(StaffProfileException.class,
                () -> service.create(staffInput(doctorAccount, "Dr An", PHONE), null, null)).getMessage());
        assertEquals("VALIDATION_FAILED", assertThrows(StaffProfileException.class,
                () -> service.create(doctorInput(receptionAccount, "Jane", true), null, null)).getMessage());
        var commaSpecialty = new DoctorInfo(List.of("Ortho,Sports"), "MD", "Bio", "Mon-Fri", true);
        assertEquals("VALIDATION_FAILED", assertThrows(StaffProfileException.class,
                () -> service.create(new StaffProfileInput(doctorAccount, "Dr An", PHONE, commaSpecialty), null, null))
                .getMessage());
        var oversizedBio = new DoctorInfo(List.of("Orthopedics"), "MD", "b".repeat(2001), "Mon-Fri", true);
        assertEquals("VALIDATION_FAILED", assertThrows(StaffProfileException.class,
                () -> service.create(new StaffProfileInput(doctorAccount, "Dr An", PHONE, oversizedBio), null, null))
                .getMessage());
        verify(profiles, never()).saveAndFlush(any(StaffProfileJpaEntity.class));
    }

    @Test
    void createRejectsWorkContactThatIsNeitherPhoneNumberNorEmail() {
        UUID accountId = UUID.randomUUID();
        stubAccount(accountId, "jane", AccountType.RECEPTIONIST);

        assertEquals("VALIDATION_FAILED", assertThrows(StaffProfileException.class,
                () -> service.create(staffInput(accountId, "Jane", "call-me-maybe"), null, null)).getMessage());
        assertEquals("VALIDATION_FAILED", assertThrows(StaffProfileException.class,
                () -> service.create(staffInput(accountId, "   ", PHONE), null, null)).getMessage());
        assertEquals("VALIDATION_FAILED", assertThrows(StaffProfileException.class,
                () -> service.create(staffInput(accountId, "J".repeat(101), PHONE), null, null)).getMessage());
        assertEquals("VALIDATION_FAILED", assertThrows(StaffProfileException.class,
                () -> service.create(staffInput(accountId, "Jane", null), null, null)).getMessage());
        assertEquals("VALIDATION_FAILED", assertThrows(StaffProfileException.class,
                () -> service.create(staffInput(accountId, "Jane", "x".repeat(101)), null, null)).getMessage());
        verify(profiles, never()).saveAndFlush(any(StaffProfileJpaEntity.class));
    }

    @Test
    void createNonDoctorStaffStoresTrimmedProfileWithoutDoctorFields() {
        UUID accountId = UUID.randomUUID();
        stubAccount(accountId, "jane", AccountType.RECEPTIONIST);
        when(profiles.saveAndFlush(any(StaffProfileJpaEntity.class))).thenAnswer(call -> call.getArgument(0));

        StaffProfileResponse response = service.create(staffInput(accountId, "  Jane  ", " 0912345678 "), null, null);

        ArgumentCaptor<StaffProfileJpaEntity> saved = ArgumentCaptor.forClass(StaffProfileJpaEntity.class);
        verify(profiles).saveAndFlush(saved.capture());
        assertEquals("Jane", saved.getValue().displayName());
        assertEquals(PHONE, saved.getValue().workContact());
        assertEquals("ACTIVE", saved.getValue().status());
        assertFalse(saved.getValue().isDoctor());
        assertEquals("RECEPTIONIST", response.role());
        assertNull(response.doctor());
        assertNull(response.isPublic());
        assertNull(response.publicUrl());
        assertEquals(List.of("edit", "remove"), response.allowedActions());
    }

    @Test
    void createDoctorStoresDoctorDetailsAndPublicPhotoPath() {
        UUID accountId = UUID.randomUUID();
        stubAccount(accountId, "dr.an", AccountType.DOCTOR);
        when(profiles.saveAndFlush(any(StaffProfileJpaEntity.class))).thenAnswer(call -> call.getArgument(0));

        StaffProfileResponse response = service.create(doctorInput(accountId, "Dr An", true), PNG, "image/png");

        ArgumentCaptor<StaffProfileJpaEntity> saved = ArgumentCaptor.forClass(StaffProfileJpaEntity.class);
        verify(profiles).saveAndFlush(saved.capture());
        StaffProfileJpaEntity profile = saved.getValue();
        verify(storage).put(argThat(k -> k.startsWith("staff-photos/" + profile.id() + "/") && k.endsWith(".png")),
                eq("image/png"), eq(PNG));
        assertTrue(profile.isDoctor());
        assertTrue(profile.publiclyVisible());
        assertTrue(profile.inActiveDirectory());
        assertFalse(profile.acceptingNewBookings());
        assertEquals("image/png", profile.photoContentType());
        assertEquals("/api/v1/staff-profiles/" + profile.id() + "/photo", profile.photoUrl());
        assertEquals("ORTHOPEDIC_DOCTOR", response.role());
        assertEquals(List.of("Orthopedics", "Sports"), response.doctor().specialties());
        assertEquals(List.of("edit", "remove", "togglePublic"), response.allowedActions());
        assertEquals("/doctors/" + profile.id(), response.publicUrl());
        assertTrue(response.isPublic());
        assertFalse(response.acceptingNewBookings());
    }

    @Test
    void updateRejectsProfileThatIsNotActive() {
        UUID id = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        StaffProfileJpaEntity removed = staffProfile(id, accountId, "Jane");
        removed.remove();
        when(profiles.findById(id)).thenReturn(Optional.of(removed));

        StaffProfileException ex = assertThrows(StaffProfileException.class,
                () -> service.update(id, staffInput(accountId, "Jane", PHONE), null, null));

        assertEquals("STAFF_PROFILE_CONFLICT", ex.getMessage());
        verify(profiles, never()).saveAndFlush(any(StaffProfileJpaEntity.class));
    }

    @Test
    void updateDoctorRoleOnNonDoctorProfileIsNotFound() {
        UUID id = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        when(profiles.findById(id)).thenReturn(Optional.of(staffProfile(id, accountId, "Old Name")));
        stubAccount(accountId, "dr.an", AccountType.DOCTOR);

        StaffProfileException ex = assertThrows(StaffProfileException.class,
                () -> service.update(id, doctorInput(accountId, "Dr New", true), null, null));

        assertEquals("STAFF_PROFILE_NOT_FOUND", ex.getMessage());
        verify(profiles, never()).saveAndFlush(any(StaffProfileJpaEntity.class));
    }

    @Test
    void updateNonDoctorProfileChangesTrimmedNameAndContact() {
        UUID id = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        StaffProfileJpaEntity profile = staffProfile(id, accountId, "Old Name");
        when(profiles.findById(id)).thenReturn(Optional.of(profile));
        stubAccount(accountId, "jane", AccountType.RECEPTIONIST);
        when(profiles.saveAndFlush(profile)).thenReturn(profile);

        StaffProfileResponse response = service.update(id, staffInput(accountId, "  New Name ", "new@example.test"), null, null);

        assertEquals("New Name", profile.displayName());
        assertEquals("new@example.test", profile.workContact());
        assertEquals("New Name", response.displayName());
        verify(profiles).saveAndFlush(profile);
    }

    @Test
    void updateDoctorProfileReplacesDoctorDetailsAndRefreshesPhotoPath() {
        UUID id = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        StaffProfileJpaEntity doctor = doctorProfile(id, accountId, "Dr An", true);
        doctor.photo("staff-photos/" + id + "/existing.png", "image/png");
        when(profiles.findById(id)).thenReturn(Optional.of(doctor));
        stubAccount(accountId, "dr.an", AccountType.DOCTOR);
        when(profiles.saveAndFlush(doctor)).thenReturn(doctor);

        StaffProfileResponse response = service.update(id, doctorInput(accountId, "Dr New", false), null, null);

        assertEquals("Dr New", doctor.displayName());
        assertEquals(PHONE, doctor.workContact());
        assertEquals(List.of("Orthopedics", "Sports"), response.doctor().specialties());
        assertEquals("/api/v1/staff-profiles/" + id + "/photo", doctor.photoUrl());
        verify(profiles).saveAndFlush(doctor);
        verifyNoInteractions(storage);
    }

    @Test
    void removeIsBlockedWhileDoctorHasFutureAppointments() {
        UUID id = UUID.randomUUID();
        StaffProfileJpaEntity doctor = doctorProfile(id, UUID.randomUUID(), "Dr An", true);
        when(profiles.findById(id)).thenReturn(Optional.of(doctor));
        Instant now = Instant.now();
        when(appointments.findProtectedBookingIntervals(id))
                .thenReturn(List.of(new OccupiedBookingInterval(now, now.plus(1, ChronoUnit.DAYS))));

        StaffProfileException ex = assertThrows(StaffProfileException.class, () -> service.remove(id));

        assertEquals("DOCTOR_HAS_FUTURE_APPOINTMENTS", ex.getMessage());
        assertEquals("ACTIVE", doctor.status());
        verify(profiles, never()).save(any(StaffProfileJpaEntity.class));
    }

    @Test
    void removeDoctorWithOnlyPastBookingsDisablesDirectoryAndMarksRemoved() {
        UUID id = UUID.randomUUID();
        StaffProfileJpaEntity doctor = doctorProfile(id, UUID.randomUUID(), "Dr An", true);
        when(profiles.findById(id)).thenReturn(Optional.of(doctor));
        when(appointments.findProtectedBookingIntervals(id)).thenReturn(List.of(new OccupiedBookingInterval(
                Instant.parse("2000-01-01T09:00:00Z"), Instant.parse("2000-01-01T09:30:00Z"))));

        service.remove(id);

        assertEquals("REMOVED", doctor.status());
        assertFalse(doctor.publiclyVisible());
        assertFalse(doctor.acceptingNewBookings());
        assertFalse(doctor.inActiveDirectory());
        verify(profiles).save(doctor);
    }

    @Test
    void removeNonDoctorStaffMarksRemovedWithoutConsultingAppointments() {
        UUID id = UUID.randomUUID();
        StaffProfileJpaEntity staff = staffProfile(id, UUID.randomUUID(), "Jane");
        when(profiles.findById(id)).thenReturn(Optional.of(staff));

        service.remove(id);

        assertEquals("REMOVED", staff.status());
        verifyNoInteractions(appointments);
        verify(profiles).save(staff);
    }

    @Test
    void savePhotoRejectsUnsupportedTypeOversizedOrMismatchedImageBeforeSaving() {
        UUID id = UUID.randomUUID();
        when(profiles.findById(id)).thenReturn(Optional.of(staffProfile(id, UUID.randomUUID(), "Jane")));
        byte[] jpegMagic = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0};
        byte[] oversized = new byte[2 * 1024 * 1024 + 1];
        oversized[0] = (byte) 0xFF;
        oversized[1] = (byte) 0xD8;
        oversized[2] = (byte) 0xFF;
        byte[] pngBytesSentAsJpeg = {(byte) 0x89, 'P', 'N', 'G', 0, 0, 0, 0};
        byte[] webpWrongFormat = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'X'};
        byte[] shortPng = {(byte) 0x89, 'P', 'N', 'G'};
        byte[] jpegWrongThirdByte = {(byte) 0xFF, (byte) 0xD8, 0};

        assertEquals("VALIDATION_FAILED", assertThrows(StaffProfileException.class,
                () -> service.savePhoto(id, null, null)).getMessage());
        assertEquals("VALIDATION_FAILED", assertThrows(StaffProfileException.class,
                () -> service.savePhoto(id, PNG, null)).getMessage());
        assertEquals("VALIDATION_FAILED", assertThrows(StaffProfileException.class,
                () -> service.savePhoto(id, new byte[0], "image/png")).getMessage());
        assertEquals("VALIDATION_FAILED", assertThrows(StaffProfileException.class,
                () -> service.savePhoto(id, jpegMagic, "image/gif")).getMessage());
        assertEquals("VALIDATION_FAILED", assertThrows(StaffProfileException.class,
                () -> service.savePhoto(id, oversized, "image/jpeg")).getMessage());
        assertEquals("VALIDATION_FAILED", assertThrows(StaffProfileException.class,
                () -> service.savePhoto(id, pngBytesSentAsJpeg, "image/jpeg")).getMessage());
        assertEquals("VALIDATION_FAILED", assertThrows(StaffProfileException.class,
                () -> service.savePhoto(id, webpWrongFormat, "image/webp")).getMessage());
        assertEquals("VALIDATION_FAILED", assertThrows(StaffProfileException.class,
                () -> service.savePhoto(id, shortPng, "image/png")).getMessage());
        assertEquals("VALIDATION_FAILED", assertThrows(StaffProfileException.class,
                () -> service.savePhoto(id, jpegWrongThirdByte, "image/jpeg")).getMessage());
        verify(profiles, never()).saveAndFlush(any(StaffProfileJpaEntity.class));
        verifyNoInteractions(storage);
    }

    @Test
    void savePhotoStoresValidWebpAndRefreshesDoctorPhotoUrl() {
        UUID id = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        StaffProfileJpaEntity doctor = doctorProfile(id, accountId, "Dr An", true);
        when(profiles.findById(id)).thenReturn(Optional.of(doctor));
        stubAccount(accountId, "dr.an", AccountType.DOCTOR);
        when(profiles.saveAndFlush(doctor)).thenReturn(doctor);
        byte[] webp = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};

        StaffProfileResponse response = service.savePhoto(id, webp, "image/webp");

        verify(storage).put(argThat(k -> k.startsWith("staff-photos/" + id + "/") && k.endsWith(".webp")),
                eq("image/webp"), eq(webp));
        assertEquals("image/webp", doctor.photoContentType());
        assertEquals("/api/v1/staff-profiles/" + id + "/photo", doctor.photoUrl());
        assertEquals("/api/v1/staff-profiles/" + id + "/photo", response.photoUrl());
        verify(profiles).saveAndFlush(doctor);
    }

    @Test
    void photoUploadFailureCleansUpNewObjectAndReplacementDeletesPreviousAfterSave() {
        UUID id = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        StaffProfileJpaEntity staff = staffProfile(id, accountId, "Jane");
        String oldKey = "staff-photos/" + id + "/old.png";
        staff.photo(oldKey, "image/png");
        when(profiles.findById(id)).thenReturn(Optional.of(staff));
        stubAccount(accountId, "jane", AccountType.RECEPTIONIST);

        doThrow(new IllegalStateException("db down")).when(profiles).saveAndFlush(staff);
        assertThrows(IllegalStateException.class, () -> service.savePhoto(id, PNG, "image/png"));
        verify(storage).delete(argThat(k -> !oldKey.equals(k) && k.startsWith("staff-photos/" + id + "/")));
        verify(storage, never()).delete(oldKey);

        staff.photo(oldKey, "image/png");
        org.mockito.Mockito.doReturn(staff).when(profiles).saveAndFlush(staff);
        TransactionSynchronizationManager.initSynchronization();
        service.savePhoto(id, PNG, "image/png");
        verify(storage, never()).delete(oldKey);
        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCommit();
        }
        verify(storage).delete(oldKey);
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void photoRejectsRemovedProfileOrProfileWithoutImage() {
        UUID removedId = UUID.randomUUID();
        StaffProfileJpaEntity removed = staffProfile(removedId, UUID.randomUUID(), "Jane");
        removed.photo("staff-photos/" + removedId + "/a.png", "image/png");
        removed.remove();
        UUID noImageId = UUID.randomUUID();
        UUID missingObjectId = UUID.randomUUID();
        StaffProfileJpaEntity missingObj = staffProfile(missingObjectId, UUID.randomUUID(), "Bob");
        missingObj.photo("staff-photos/" + missingObjectId + "/missing.png", "image/png");
        when(profiles.findById(removedId)).thenReturn(Optional.of(removed));
        when(profiles.findById(noImageId)).thenReturn(Optional.of(staffProfile(noImageId, UUID.randomUUID(), "Jane")));
        when(profiles.findById(missingObjectId)).thenReturn(Optional.of(missingObj));
        when(storage.get("staff-photos/" + missingObjectId + "/missing.png")).thenReturn(Optional.empty());

        assertEquals("STAFF_PROFILE_NOT_FOUND",
                assertThrows(StaffProfileException.class, () -> service.photo(removedId, true)).getMessage());
        assertEquals("STAFF_PROFILE_NOT_FOUND",
                assertThrows(StaffProfileException.class, () -> service.photo(noImageId, true)).getMessage());
        assertEquals("STAFF_PROFILE_NOT_FOUND",
                assertThrows(StaffProfileException.class, () -> service.photo(missingObjectId, true)).getMessage());
    }

    @Test
    void publicPhotoRequestIsDeniedForNonDoctorAndHiddenDoctor() {
        UUID staffId = UUID.randomUUID();
        StaffProfileJpaEntity staff = staffProfile(staffId, UUID.randomUUID(), "Jane");
        staff.photo("staff-photos/" + staffId + "/a.png", "image/png");
        UUID hiddenId = UUID.randomUUID();
        StaffProfileJpaEntity hidden = doctorProfile(hiddenId, UUID.randomUUID(), "Dr Hidden", false);
        hidden.photo("staff-photos/" + hiddenId + "/a.png", "image/png");
        UUID inactiveDirId = UUID.randomUUID();
        StaffProfileJpaEntity inactiveDir = doctorProfile(inactiveDirId, UUID.randomUUID(), "Dr Inactive", true);
        inactiveDir.photo("staff-photos/" + inactiveDirId + "/a.png", "image/png");
        inactiveDir.updateDirectoryState(true, true, false);
        when(profiles.findById(staffId)).thenReturn(Optional.of(staff));
        when(profiles.findById(hiddenId)).thenReturn(Optional.of(hidden));
        when(profiles.findById(inactiveDirId)).thenReturn(Optional.of(inactiveDir));

        assertEquals("STAFF_PROFILE_NOT_FOUND",
                assertThrows(StaffProfileException.class, () -> service.photo(staffId, false)).getMessage());
        assertEquals("STAFF_PROFILE_NOT_FOUND",
                assertThrows(StaffProfileException.class, () -> service.photo(hiddenId, false)).getMessage());
        assertEquals("STAFF_PROFILE_NOT_FOUND",
                assertThrows(StaffProfileException.class, () -> service.photo(inactiveDirId, false)).getMessage());
    }

    @Test
    void adminPhotoRequestReturnsPrivatePhotoAndPubliclyVisibleDoctorPhotoIsServed() {
        UUID staffId = UUID.randomUUID();
        String staffKey = "staff-photos/" + staffId + "/a.png";
        StaffProfileJpaEntity staff = staffProfile(staffId, UUID.randomUUID(), "Jane");
        staff.photo(staffKey, "image/png");
        UUID doctorId = UUID.randomUUID();
        String doctorKey = "staff-photos/" + doctorId + "/b.png";
        StaffProfileJpaEntity visibleDoctor = doctorProfile(doctorId, UUID.randomUUID(), "Dr An", true);
        visibleDoctor.photo(doctorKey, "image/png");
        when(profiles.findById(staffId)).thenReturn(Optional.of(staff));
        when(profiles.findById(doctorId)).thenReturn(Optional.of(visibleDoctor));
        when(storage.get(staffKey)).thenReturn(Optional.of(PNG));
        when(storage.get(doctorKey)).thenReturn(Optional.of(PNG));

        var adminPhoto = service.photo(staffId, true);
        var publicPhoto = service.photo(doctorId, false);
        assertArrayEquals(PNG, adminPhoto.content());
        assertEquals("image/png", adminPhoto.contentType());
        assertArrayEquals(PNG, publicPhoto.content());
        assertEquals("image/png", publicPhoto.contentType());
    }
}
