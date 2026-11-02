package fu.fa26.sep490.g58.otcms_be.patient;

import fu.fa26.sep490.g58.otcms_be.auth.service.*;
import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory.ProfileRecord;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.patient.exception.PatientProfileException;
import fu.fa26.sep490.g58.otcms_be.patient.service.impl.PatientProfileServiceImpl;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PatientProfileServiceTest {
    final PatientProfileDirectory directory = mock(PatientProfileDirectory.class);
    final ReceptionistAccountAccessService receptionists = mock(ReceptionistAccountAccessService.class);
    final CoordinationGuardService guard = mock(CoordinationGuardService.class);
    final PatientProfileServiceImpl service = new PatientProfileServiceImpl(directory, guard, receptionists);
    final UUID actor = UUID.randomUUID(), id = UUID.randomUUID();
    final LocalDate dob = LocalDate.of(2000, 1, 2);
    final String phone = "+84912345678";

    @BeforeEach void active() { when(receptionists.isActiveReceptionistAccount(actor)).thenReturn(true); }
    ProfileRecord record(String name) { return new ProfileRecord(id, null, name, phone, "PATIENT", dob, "OTHER"); }
    void code(String expected, org.junit.jupiter.api.function.Executable action) {
        assertEquals(expected, assertThrows(PatientProfileException.class, action).getMessage());
    }
    @Test void noMatchesReturnsEmpty() {
        when(directory.match(phone, dob, null)).thenReturn(List.of());
        assertTrue(service.matches(actor, "0912 345 678", dob, null).isEmpty());
    }
    @Test void oneMatchIgnoresFullNameBecauseItIsOnlyARefinement() {
        when(directory.match(phone, dob, null)).thenReturn(List.of(record("Tên khác")));
        assertEquals(id, service.matches(actor, phone, dob, "irrelevant").getFirst().id());
        verify(directory, never()).match(phone, dob, "irrelevant");
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"  "})
    void multipleWithoutNameIsAmbiguous(String name) {
        when(directory.match(phone, dob, null)).thenReturn(List.of(record("An"), record("Bình")));
        code("PATIENT_MATCH_AMBIGUOUS", () -> service.matches(actor, phone, dob, name));
    }
    @Test void refinesNormalizedNameToOneOrZeroOrAmbiguous() {
        when(directory.match(phone, dob, null)).thenReturn(List.of(record("An"), record("Bình")));
        String decomposed = java.text.Normalizer.normalize(" Nguyễn  An ", java.text.Normalizer.Form.NFD);
        when(directory.match(phone, dob, "Nguyễn An")).thenReturn(List.of(record("Nguyễn An")));
        assertEquals(1, service.matches(actor, phone, dob, decomposed).size());
        when(directory.match(phone, dob, "Nguyễn An")).thenReturn(List.of());
        assertTrue(service.matches(actor, phone, dob, decomposed).isEmpty());
        when(directory.match(phone, dob, "Nguyễn An")).thenReturn(List.of(record("Nguyễn An"), record("Nguyễn An")));
        code("PATIENT_MATCH_AMBIGUOUS", () -> service.matches(actor, phone, dob, decomposed));
    }
    @ParameterizedTest @ValueSource(strings = {"0912345678", "+84 (912) 345-678", "0084912345678", "84912345678"})
    void canonicalPhoneEquality(String raw) {
        service.matches(actor, raw, dob, null);
        verify(directory).match(phone, dob, null);
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"abc", "+", "123", "0912x345678"})
    void invalidPhone(String raw) { code("VALIDATION_FAILED", () -> service.matches(actor, raw, dob, null)); }
    @Test void invalidBirthdate() {
        code("VALIDATION_FAILED", () -> service.matches(actor, phone, null, null));
        code("VALIDATION_FAILED", () -> service.matches(actor, phone, LocalDate.now().plusDays(2), null));
        verifyNoInteractions(directory);
    }
    @Test void createNormalizesAndPersistsGuestWithAdministrativeFields() {
        when(directory.create(any(), eq("GUARDIAN"))).thenAnswer(call -> call.getArgument(0));
        var result = service.create(actor, "  Nguyễn   An  ", "0912345678", "guardian", dob,
                "other", " Address ", null, null);
        assertEquals("Nguyễn An", result.fullName());
        assertEquals("Address", result.address());
        assertFalse(result.hasAccount());
        assertNull(result.nationalId());
        var order = inOrder(guard, directory);
        order.verify(guard).acquire();
        order.verify(directory).duplicateExists(phone, dob, "Nguyễn An");
        order.verify(directory).create(argThat(p -> p.accountId() == null), eq("GUARDIAN"));
    }
    @Test void createDuplicateRejectedBeforeInsert() {
        when(directory.duplicateExists(phone, dob, "Nguyễn An")).thenReturn(true);
        code("PATIENT_PROFILE_DUPLICATE", () -> service.create(actor, " Nguyễn  An ", phone,
                "PATIENT", dob, "OTHER", "Address", null, null));
        verify(directory, never()).create(any(), any());
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"  "})
    void createRequiresAddress(String address) {
        code("VALIDATION_FAILED", () -> service.create(actor, "Nguyễn An", phone,
                "PATIENT", dob, "OTHER", address, null, null));
        verifyNoInteractions(guard, directory);
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"INVALID", "cccd"})
    void identifierRequiresValidType(String type) {
        code("VALIDATION_FAILED", () -> service.create(actor, "Nguyễn An", phone,
                "PATIENT", dob, "OTHER", "Address", "123", type));
    }
    @Test void detailMissingAndAccountProjection() {
        when(directory.findProfile(id)).thenReturn(Optional.empty());
        code("PATIENT_PROFILE_NOT_FOUND", () -> service.detail(actor, id));
        when(directory.findProfile(id)).thenReturn(Optional.of(new ProfileRecord(id, actor, "An", phone,
                "PATIENT", dob, "OTHER", null, "123", null, 4)));
        var detail = service.detail(actor, id);
        assertTrue(detail.hasAccount()); assertEquals(4, detail.version()); assertNull(detail.address());
    }
    @Test void updatePreservesAccountAndAllowsLegacyNullAddress() {
        when(directory.findProfile(id)).thenReturn(Optional.of(new ProfileRecord(id, actor, "An", phone,
                "PATIENT", dob, "OTHER", null, null, null, 4)));
        when(directory.update(any(), eq(4L))).thenAnswer(call -> call.getArgument(0));
        var updated = service.update(actor, id, "Bình", phone, "GUARDIAN", dob, "FEMALE", null, null, null, 4L);
        assertTrue(updated.hasAccount()); assertNull(updated.address());
        verify(directory).update(argThat(p -> actor.equals(p.accountId())), eq(4L));
        verify(directory, never()).duplicateExists(any(), any(), any());
        code("STALE_VERSION", () -> service.update(actor, id, "Bình", phone, "PATIENT", dob, "OTHER", null, null, null, 3L));
    }
    @Test void invalidUpdateVersionAndDocument() {
        code("VALIDATION_FAILED", () -> service.update(actor, id, "An", phone, "PATIENT", dob, "OTHER", null, null, null, null));
        code("VALIDATION_FAILED", () -> service.update(actor, id, "An", phone, "PATIENT", dob, "OTHER", null, null, null, -1L));
        when(directory.findProfile(id)).thenReturn(Optional.of(record("An")));
        code("VALIDATION_FAILED", () -> service.update(actor, id, "An", phone, "PATIENT", dob, "OTHER", null, "123", null, 0L));
    }
    @Test void inactiveReceptionistDeniedBeforeReadingProfiles() {
        when(receptionists.isActiveReceptionistAccount(actor)).thenReturn(false);
        code("ACCESS_DENIED", () -> service.matches(actor, phone, dob, null));
        code("ACCESS_DENIED", () -> service.detail(actor, id));
        code("ACCESS_DENIED", () -> service.create(actor, "An", phone, "PATIENT", dob, "OTHER", "Address", null, null));
        code("ACCESS_DENIED", () -> service.update(actor, id, "An", phone, "PATIENT", dob, "OTHER", null, null, null, 0L));
        verifyNoInteractions(directory, guard);
    }
}
