package fu.fa26.sep490.g58.otcms_be.content;
import fu.fa26.sep490.g58.otcms_be.content.exception.*;
import fu.fa26.sep490.g58.otcms_be.activitylog.exception.*;
import fu.fa26.sep490.g58.otcms_be.reporting.exception.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ScopedExceptionMappingTest {
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    @Test void everyContentBusinessReasonMapsToTheExistingStatusAndMessage() {
        var handler = new ContentApiExceptionHandler();
        for (var reason : ContentOperationException.Reason.values()) {
            int expected = switch (reason) {
                case NOT_FOUND -> 404;
                case FORBIDDEN -> 403;
                case CONFLICT -> 409;
                case DELIVERY_UNAVAILABLE -> 503;
                default -> 400;
            };
            var response = handler.failure(new ContentOperationException(reason), request);
            assertEquals(expected, response.getStatusCode().value());
            assertEquals(reason.code(), response.getBody().error().code());
        }
        assertEquals(409, handler.conflict(request).getStatusCode().value());
    }
    @Test void activityCriteriaNotFoundAndStorageFailuresKeepTheirMappings() {
        var handler = new ActivityLogApiExceptionHandler();
        assertEquals(400, handler.business(new ActivityLogOperationException("MSG-54"), request).getStatusCode().value());
        assertEquals(404, handler.business(new ActivityLogOperationException("MSG-50"), request).getStatusCode().value());
        assertEquals("MSG-04", handler.unavailable(request).getBody().error().code());
        assertEquals(500, handler.unavailable(request).getStatusCode().value());
    }
    @Test void reportCriteriaAndUnavailableExportKeepTheirMappings() {
        var handler = new ReportingApiExceptionHandler();
        for (var reason : ReportingOperationException.Reason.values()) {
            var response = handler.business(new ReportingOperationException(reason), request);
            assertEquals(reason == ReportingOperationException.Reason.EXPORT_UNAVAILABLE ? 503 : 400, response.getStatusCode().value());
            assertEquals(reason == ReportingOperationException.Reason.EXPORT_UNAVAILABLE ? "MSG-04" : "MSG-72", response.getBody().error().code());
        }
    }
    @Test void malformedContentAndAnnouncementRequestsKeepDistinctMessages() {
        var handler = new ContentApiExceptionHandler();
        request.setRequestURI("/api/v1/admin/website-contents");
        assertEquals("MSG-69", handler.invalidBody(request).getBody().error().code());
        request.setRequestURI("/api/v1/admin/announcements");
        assertEquals("MSG-71", handler.invalidBody(request).getBody().error().code());
        assertEquals("MSG-04", handler.unavailable(request).getBody().error().code());
    }
}
