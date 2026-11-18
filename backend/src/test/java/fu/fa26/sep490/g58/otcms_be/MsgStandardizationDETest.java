package fu.fa26.sep490.g58.otcms_be;

import static org.junit.jupiter.api.Assertions.assertEquals;

import fu.fa26.sep490.g58.otcms_be.diagnostic.exception.DiagnosticApiExceptionHandler;
import fu.fa26.sep490.g58.otcms_be.diagnostic.exception.DiagnosticOperationException;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingApiExceptionHandler;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationException;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.exception.ClinicServiceException;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.exception.ClinicServiceExceptionHandler;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitApiExceptionHandler;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class MsgStandardizationDETest {
    @Test
    void roomCodesKeepStatusesAndOtherVisitEndpointsKeepTheirCodes() {
        var handler = new VisitApiExceptionHandler();
        var room = new MockHttpServletRequest("POST", "/api/v1/admin/visit-rooms");
        var duplicate = handler.handle(new VisitOperationException("DUPLICATE_ROOM_CODE"), room);
        assertEquals(409, duplicate.getStatusCode().value());
        assertEquals("MSG-92", duplicate.getBody().error().code());
        assertEquals("MSG-64", handler.handle(new VisitOperationException("ROOM_HAS_ACTIVE_RESERVATIONS"), room).getBody().error().code());
        assertEquals("MSG-62", handler.handle(new VisitOperationException("VALIDATION_FAILED"), room).getBody().error().code());
        assertEquals("MSG-50", handler.handle(new VisitOperationException("ACCESS_DENIED"), room).getBody().error().code());
        assertEquals("MSG-04", handler.handle(new VisitOperationException("DEPENDENCY_UNAVAILABLE"), room).getBody().error().code());
        var other = new MockHttpServletRequest("POST", "/api/v1/visits");
        assertEquals("VALIDATION_FAILED", handler.handle(new VisitOperationException("VALIDATION_FAILED"), other).getBody().error().code());
    }

    @Test
    void catalogCodesKeepStatusAndDoNotChangeOtherEndpoints() {
        var handler = new ClinicServiceExceptionHandler();
        var request = new MockHttpServletRequest("POST", "/api/v1/admin/clinic-services");
        var duplicate = handler.handle(new ClinicServiceException("DUPLICATE_SERVICE_NAME"), request);
        assertEquals(409, duplicate.getStatusCode().value());
        assertEquals("MSG-66", duplicate.getBody().error().code());
        assertEquals("MSG-50", handler.handle(new ClinicServiceException("SERVICE_NOT_FOUND"), request).getBody().error().code());
        assertEquals("MSG-04", handler.handle(new ClinicServiceException("DEPENDENCY_UNAVAILABLE"), request).getBody().error().code());
        var update = new MockHttpServletRequest("PUT", "/api/v1/admin/clinic-services/a");
        assertEquals("MSG-50", handler.handle(new ClinicServiceException("SERVICE_NOT_FOUND"), update).getBody().error().code());
        var options = new MockHttpServletRequest("GET", "/api/v1/admin/clinic-services/room-options");
        assertEquals("SERVICE_NOT_FOUND", handler.handle(new ClinicServiceException("SERVICE_NOT_FOUND"), options).getBody().error().code());
        var other = new MockHttpServletRequest("GET", "/api/v1/staff/clinic-services");
        assertEquals("SERVICE_NOT_FOUND", handler.handle(new ClinicServiceException("SERVICE_NOT_FOUND"), other).getBody().error().code());
    }

    @Test
    void ingestAndFindingsMapErrorsWithoutChangingStatus() {
        var imaging = new ImagingApiExceptionHandler();
        var ingest = new MockHttpServletRequest("POST", "/api/v1/imaging/requests/a/attempts/b/ingest-operations");
        var invalid = imaging.operation(new ImagingOperationException(ImagingOperationOutcome.FILE_INVALID), ingest);
        assertEquals(422, invalid.getStatusCode().value());
        assertEquals("MSG-47", invalid.getBody().error().code());
        assertEquals("MSG-40", imaging.operation(new ImagingOperationException(ImagingOperationOutcome.UNAUTHORIZED_RECORD), ingest).getBody().error().code());
        assertEquals("MSG-42", imaging.operation(new ImagingOperationException(ImagingOperationOutcome.STATE_CONFLICT), ingest).getBody().error().code());
        assertEquals("MSG-04", imaging.operation(new ImagingOperationException(ImagingOperationOutcome.DEPENDENCY_UNAVAILABLE), ingest).getBody().error().code());
        var diagnostic = new DiagnosticApiExceptionHandler();
        var findings = new MockHttpServletRequest("POST", "/api/v1/imaging-requests/a/findings");
        assertEquals("MSG-39", diagnostic.operation(new DiagnosticOperationException(DiagnosticOperationOutcome.EVIDENCE_MISMATCH), findings).getBody().error().code());
        assertEquals("MSG-40", diagnostic.operation(new DiagnosticOperationException(DiagnosticOperationOutcome.UNAUTHORIZED_RECORD), findings).getBody().error().code());
        assertEquals("MSG-41", diagnostic.operation(new DiagnosticOperationException(DiagnosticOperationOutcome.RECORD_NOT_FOUND), findings).getBody().error().code());
        var reports = new MockHttpServletRequest("POST", "/api/v1/imaging-requests/a/reports");
        assertEquals("MSG-47", diagnostic.operation(new DiagnosticOperationException(DiagnosticOperationOutcome.EVIDENCE_MISMATCH), reports).getBody().error().code());
    }
}
