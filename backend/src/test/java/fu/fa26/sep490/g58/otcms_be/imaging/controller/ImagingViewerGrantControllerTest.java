package fu.fa26.sep490.g58.otcms_be.imaging.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationException;
import fu.fa26.sep490.g58.otcms_be.imaging.service.impl.ImagingViewerGrantService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class ImagingViewerGrantControllerTest {
    @Mock ImagingViewerGrantService grants;

    @Test
    void doctorReceivesOnlyScopedSecureHttpOnlyCookieAndNoStoreResponse() {
        UUID actorId = UUID.randomUUID();
        String rawToken = "opaque-secret-token";
        when(grants.issue(actorId, "study-1", "DOCTOR_REVIEW", "unknown"))
                .thenReturn(new ImagingViewerGrantService.IssuedGrant("OTCMS_VIEW_GRANT_abc", rawToken,
                        Instant.now().plusSeconds(120)));
        var controller = new ImagingViewerGrantController(grants);

        var response = controller.issue("study-1", new ImagingViewerGrantController.ViewerGrantRequest("DOCTOR_REVIEW"),
                new AuthenticatedAccount(actorId, AccountRole.DOCTOR, UUID.randomUUID()),
                new MockHttpServletRequest());

        assertEquals(201, response.getStatusCode().value());
        assertEquals("no-store", response.getHeaders().getCacheControl());
        String cookie = response.getHeaders().getFirst("Set-Cookie");
        org.junit.jupiter.api.Assertions.assertNotNull(cookie);
        org.junit.jupiter.api.Assertions.assertTrue(cookie.contains("HttpOnly"));
        org.junit.jupiter.api.Assertions.assertTrue(cookie.contains("Secure"));
        org.junit.jupiter.api.Assertions.assertTrue(cookie.contains("SameSite=Strict"));
        org.junit.jupiter.api.Assertions.assertTrue(cookie.contains("Path=/dicom-web/"));
        assertFalse(response.getBody().toString().contains(rawToken));
        verify(grants).issue(actorId, "study-1", "DOCTOR_REVIEW", "unknown");
    }

    @Test
    void technicianAndWrongPurposeAreRejectedBeforeGrantIssuance() {
        var controller = new ImagingViewerGrantController(grants);
        var request = new MockHttpServletRequest();

        assertThrows(ImagingOperationException.class, () -> controller.issue("study-1",
                new ImagingViewerGrantController.ViewerGrantRequest("DOCTOR_REVIEW"),
                new AuthenticatedAccount(UUID.randomUUID(), AccountRole.IMAGING_TECHNICIAN, UUID.randomUUID()), request));
        assertThrows(ImagingOperationException.class, () -> controller.issue("study-1",
                new ImagingViewerGrantController.ViewerGrantRequest("PATIENT_VIEW"),
                new AuthenticatedAccount(UUID.randomUUID(), AccountRole.DOCTOR, UUID.randomUUID()), request));

        verifyNoInteractions(grants);
    }
}
