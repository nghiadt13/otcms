package fu.fa26.sep490.g58.otcms_be.imaging.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.ViewAuthorization;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.ImagingStudyPort;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationException;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingStudyJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingViewerGrantJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingStudyRepository;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingViewerGrantRepository;
import fu.fa26.sep490.g58.otcms_be.imaging.service.impl.ImagingAuditPersistenceService;
import fu.fa26.sep490.g58.otcms_be.imaging.service.impl.ImagingViewerGrantService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class ImagingViewerGrantServiceTest {
    @Mock ImagingStudyRepository studies;
    @Mock ImagingViewerGrantRepository grants;
    @Mock ObjectProvider<ImagingStudyPort> ownerPorts;
    @Mock ImagingStudyPort owner;
    @Mock ImagingAuditPersistenceService audit;

    @Test
    void storesOnlyHashedTokenAndCapsExpiryAtFiveMinutes() throws Exception {
        String studyRef = "study-allowed";
        UUID actor = UUID.randomUUID();
        when(studies.findById(studyRef)).thenReturn(Optional.of(study(studyRef)));
        when(ownerPorts.getIfAvailable()).thenReturn(owner);
        when(owner.authorizeStudyView(studyRef, actor, "DOCTOR_REVIEW"))
                .thenReturn(new ViewAuthorization(true, Instant.now().plusSeconds(900), "review-v2"));
        when(grants.saveAndFlush(any(ImagingViewerGrantJpaEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var issued = new ImagingViewerGrantService(studies, grants, ownerPorts, audit)
                .issue(actor, studyRef, "DOCTOR_REVIEW", "corr-1");

        assertEquals(43, issued.token().length());
        assertFalse(issued.expiresAt().isAfter(Instant.now().plusSeconds(301)));
        ArgumentCaptor<ImagingViewerGrantJpaEntity> persisted = ArgumentCaptor.forClass(ImagingViewerGrantJpaEntity.class);
        verify(grants).saveAndFlush(persisted.capture());
        String expectedHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(issued.token().getBytes(StandardCharsets.UTF_8)));
        assertEquals(expectedHash, persisted.getValue().getTokenSha256());
        assertNotEquals(issued.token(), persisted.getValue().getTokenSha256());
        assertEquals(studyRef, persisted.getValue().getStudyRef());
        assertEquals(actor, persisted.getValue().getActorAccountId());
        assertEquals("DOCTOR_REVIEW", persisted.getValue().getPurpose());
        assertEquals("review-v2", persisted.getValue().getAuthorizedVersionRef());
        assertEquals(issued.expiresAt(), persisted.getValue().getExpiresAt());
        verify(grants).revokeActive(org.mockito.ArgumentMatchers.eq(studyRef),
                org.mockito.ArgumentMatchers.eq(actor), any());
    }

    @Test
    void deniesWhenOwnerDecisionIsUnavailableAndDoesNotPersistGrant() {
        String studyRef = "study-private";
        when(studies.findById(studyRef)).thenReturn(Optional.of(study(studyRef)));
        when(ownerPorts.getIfAvailable()).thenReturn(null);

        ImagingOperationException unavailable = assertThrows(ImagingOperationException.class,
                () -> new ImagingViewerGrantService(studies, grants, ownerPorts, audit)
                        .issue(UUID.randomUUID(), studyRef, "PATIENT_VIEW", "corr-2"));

        assertEquals(ImagingOperationOutcome.DEPENDENCY_UNAVAILABLE, unavailable.outcome());
        verifyNoInteractions(grants, audit);
    }

    @Test
    void deniesOwnerDecisionWithoutAuthorizedVersion() {
        String studyRef = "study-denied";
        UUID actor = UUID.randomUUID();
        when(studies.findById(studyRef)).thenReturn(Optional.of(study(studyRef)));
        when(ownerPorts.getIfAvailable()).thenReturn(owner);
        when(owner.authorizeStudyView(studyRef, actor, "PATIENT_VIEW"))
                .thenReturn(new ViewAuthorization(true, Instant.now().plusSeconds(60), null));

        ImagingOperationException denied = assertThrows(ImagingOperationException.class,
                () -> new ImagingViewerGrantService(studies, grants, ownerPorts, audit)
                        .issue(actor, studyRef, "PATIENT_VIEW", "corr-3"));

        assertEquals(ImagingOperationOutcome.UNAUTHORIZED_RECORD, denied.outcome());
        verify(grants, org.mockito.Mockito.never()).saveAndFlush(any());
    }

    @Test
    void gatewayAuthorizationRechecksRevocationExpiryAndCurrentOwnerVersion() throws Exception {
        String studyRef = "study-live";
        String token = "A".repeat(43);
        UUID actor = UUID.randomUUID();
        Instant expiresAt = Instant.now().plusSeconds(120);
        var persisted = new ImagingViewerGrantJpaEntity(UUID.randomUUID(), sha256(token), actor,
                "DOCTOR_REVIEW", studyRef, "review-v4", Instant.now(), expiresAt, null);
        when(grants.findByTokenSha256(sha256(token))).thenReturn(Optional.of(persisted));
        when(ownerPorts.getIfAvailable()).thenReturn(owner);
        when(owner.authorizeStudyView(studyRef, actor, "DOCTOR_REVIEW"))
                .thenReturn(new ViewAuthorization(true, expiresAt, "review-v4"));

        assertEquals(true, new ImagingViewerGrantService(studies, grants, ownerPorts, audit)
                .isAuthorized(studyRef, token));

        when(owner.authorizeStudyView(studyRef, actor, "DOCTOR_REVIEW"))
                .thenReturn(new ViewAuthorization(true, expiresAt, "review-v5"));
        assertEquals(false, new ImagingViewerGrantService(studies, grants, ownerPorts, audit)
                .isAuthorized(studyRef, token));
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static ImagingStudyJpaEntity study(String studyRef) {
        return new ImagingStudyJpaEntity(studyRef, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "orthanc-ref", "1.2.3", "CT", 1, 1,
                "CONFIRMED_MATCH");
    }
}
