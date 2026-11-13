package fu.fa26.sep490.g58.otcms_be.patient;

import fu.fa26.sep490.g58.otcms_be.auth.service.*;
import fu.fa26.sep490.g58.otcms_be.auth.service.impl.EmailOtpServiceImpl;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.config.AuthProperties;
import fu.fa26.sep490.g58.otcms_be.patient.exception.*;
import fu.fa26.sep490.g58.otcms_be.patient.model.CareRelationship.Relationship;
import fu.fa26.sep490.g58.otcms_be.patient.model.dto.DependentDtos.*;
import fu.fa26.sep490.g58.otcms_be.patient.model.entity.DependentAddRequestJpaEntity;
import fu.fa26.sep490.g58.otcms_be.patient.repository.DependentAddRequestRepository;
import fu.fa26.sep490.g58.otcms_be.patient.service.DependentProfileService;
import fu.fa26.sep490.g58.otcms_be.patient.service.impl.DependentAddOtpServiceImpl;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** UC13: the OTP goes to the requester's own email and nothing is added before it is verified. */
class DependentAddOtpServiceTest {
    final UUID actor = UUID.randomUUID();
    final Instant now = Instant.parse("2026-11-04T00:00:00Z");
    final LocalDate dob = LocalDate.of(2019, 4, 3);
    final PatientIdentityDirectory patients = mock(PatientIdentityDirectory.class);
    final MyAccountQueryService accounts = mock(MyAccountQueryService.class);
    final DependentAddRequestRepository requests = mock(DependentAddRequestRepository.class);
    final EmailDeliveryService email = mock(EmailDeliveryService.class);
    final EmailOtpService otp = new EmailOtpServiceImpl();
    final DependentProfileService dependents = mock(DependentProfileService.class);
    final DependentAddOtpServiceImpl service = new DependentAddOtpServiceImpl(patients, accounts, requests,
            mock(CoordinationGuardService.class), otp, email,
            new AuthProperties(null, null, null, null, null, "v1", "v1", "test-secret"),
            Clock.fixed(now, ZoneOffset.UTC), dependents, mock(PlatformTransactionManager.class));
    final Map<UUID, DependentAddRequestJpaEntity> saved = new HashMap<>();

    @BeforeEach void setup() {
        when(patients.isActivePatient(actor)).thenReturn(true);
        when(accounts.find(actor)).thenReturn(Optional.of(new MyAccountQueryService.MyAccountView(actor,
                "PATIENT", "owner@example.test", "Owner")));
        when(requests.saveAndFlush(any())).thenAnswer(call -> {
            DependentAddRequestJpaEntity row = call.getArgument(0); saved.put(row.getId(), row); return row;
        });
        when(requests.findById(any())).thenAnswer(call -> Optional.ofNullable(saved.get(call.getArgument(0))));
        when(requests.findByIdForUpdate(any())).thenAnswer(call -> Optional.ofNullable(saved.get(call.getArgument(0))));
        when(requests.activateIfCurrent(any(), anyString(), any(), anyInt())).thenAnswer(call -> {
            var row = saved.get(call.getArgument(0));
            if (row == null) return 0;
            row.setOtpHash(call.getArgument(1)); return 1;
        });
    }

    AddDependentRequest request() {
        return new AddDependentRequest(" Phạm   Văn Hải ", dob, "male", "012345678901", "CCCD", Relationship.CHILD);
    }

    /** Request an OTP and capture the six-digit code that was emailed. */
    String requestAndCaptureCode(UUID[] requestId) {
        var result = service.request(actor, request());
        requestId[0] = result.requestId();
        var code = ArgumentCaptor.forClass(String.class);
        verify(email).sendVerificationCode(eq("owner@example.test"), code.capture(), eq(actor));
        return code.getValue();
    }

    @Test void sendsCodeToRequesterOwnEmailAndCreatesNothing() {
        var result = service.request(actor, request());

        assertEquals("***@example.test", result.maskedEmail());
        verify(email).sendVerificationCode(eq("owner@example.test"), matches("[0-9]{6}"), eq(actor));
        verifyNoInteractions(dependents);
        var row = saved.get(result.requestId());
        assertEquals("Phạm Văn Hải", row.getFullName());
        assertEquals("MALE", row.getGender());
        assertEquals("CHILD", row.getRelationship());
    }

    @Test void wrongCodeIsRejectedAndCountedWithoutAddingDependent() {
        var id = new UUID[1];
        requestAndCaptureCode(id);

        assertThrows(CareOtpInvalidException.class, () -> service.verify(actor, id[0], "000000"));
        assertEquals(1, saved.get(id[0]).getFailedAttempts());
        verifyNoInteractions(dependents);
    }

    @Test void correctCodeAddsDependentOnceWithStoredDetails() {
        var id = new UUID[1];
        String code = requestAndCaptureCode(id);
        var linked = new LinkResult("LINKED", UUID.randomUUID(), null, null);
        when(dependents.add(eq(actor), any())).thenReturn(linked);

        assertSame(linked, service.verify(actor, id[0], code));
        var added = ArgumentCaptor.forClass(AddDependentRequest.class);
        verify(dependents).add(eq(actor), added.capture());
        assertEquals("Phạm Văn Hải", added.getValue().fullName());
        assertEquals(Relationship.CHILD, added.getValue().relationship());
        // The code is single use.
        assertThrows(CareOtpInvalidException.class, () -> service.verify(actor, id[0], code));
        verify(dependents, times(1)).add(any(), any());
    }

    @Test void anotherAccountCannotVerifyTheRequest() {
        var id = new UUID[1];
        String code = requestAndCaptureCode(id);
        UUID stranger = UUID.randomUUID();
        when(patients.isActivePatient(stranger)).thenReturn(true);

        assertThrows(CareRelationshipException.class, () -> service.verify(stranger, id[0], code));
        verifyNoInteractions(dependents);
    }

    @Test void invalidDetailsFailBeforeAnyEmail() {
        var bad = new AddDependentRequest("A", dob, "male", null, null, Relationship.CHILD);

        assertThrows(PatientProfileException.class, () -> service.request(actor, bad));
        verifyNoInteractions(email);
    }

    @Test void emailFailureSupersedesTheRequestAndReportsUnavailable() {
        doThrow(new RuntimeException("smtp down")).when(email).sendVerificationCode(any(), any(), any());

        var error = assertThrows(CareRelationshipException.class, () -> service.request(actor, request()));

        assertEquals("EMAIL_DELIVERY_UNAVAILABLE", error.getMessage());
        assertTrue(saved.values().stream().allMatch(row -> row.getSupersededAt() != null));
    }
}
