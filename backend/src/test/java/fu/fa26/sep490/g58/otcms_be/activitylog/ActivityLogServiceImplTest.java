package fu.fa26.sep490.g58.otcms_be.activitylog;
import fu.fa26.sep490.g58.otcms_be.activitylog.repository.*;
import fu.fa26.sep490.g58.otcms_be.activitylog.exception.*;
import fu.fa26.sep490.g58.otcms_be.activitylog.service.impl.*;
import fu.fa26.sep490.g58.otcms_be.activitylog.service.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
class ActivityLogServiceImplTest {
    private final ActivityLogRepository reader = mock(ActivityLogRepository.class);
    private final ActivityLogServiceImpl handler = new ActivityLogServiceImpl(reader);
    @Test void rejectsInvalidDatesFiltersAndPaginationBeforeQuerying() {
        for (String[] dates : List.of(new String[]{
            "2026-11-07", "2026-11-01"
        }, new String[]{
            "not-a-date", ""
        }, new String[]{
            "2026-02-30", ""
        })) {
            assertEquals("MSG-54", assertThrows(ActivityLogOperationException.class, () -> handler.list(dates[0], dates[1], "", "", "", 0, 20)).getMessage());
        }
        assertThrows(ActivityLogOperationException.class, () -> handler.list("", "", "AUTH", "", "", 0, 20));
        assertThrows(ActivityLogOperationException.class, () -> handler.list("", "", "", "", "", -1, 20));
        assertThrows(ActivityLogOperationException.class, () -> handler.list("", "", "", "", "", 0, 101));
        verifyNoInteractions(reader);
    }
    @Test void usesInclusiveClinicDatesAndExclusiveNextDayBoundary() {
        when(reader.list(any())).thenReturn(new ActivityLogRepository.Page(List.of(), 0, 20, 0, 0));
        handler.list("2026-11-01", "2026-11-01", "ROOM", "P.101", " admin ", 0, 20);
        var criteria = ArgumentCaptor.forClass(ActivityLogRepository.Criteria.class);
        verify(reader).list(criteria.capture());
        assertEquals(Instant.parse("2026-10-31T17:00:00Z"), criteria.getValue().from());
        assertEquals(Instant.parse("2026-11-01T17:00:00Z"), criteria.getValue().until());
        assertEquals("admin", criteria.getValue().actor());
    }
    @Test void missingOrInvalidIdIsNotFoundWithoutLeakingInternalDetails() {
        String id = "room:11111111-1111-1111-1111-111111111111";
        when(reader.find(id)).thenReturn(Optional.empty());
        assertEquals("MSG-50", assertThrows(ActivityLogOperationException.class, () -> handler.detail(id)).getMessage());
        assertEquals("MSG-50", assertThrows(ActivityLogOperationException.class, () -> handler.detail("invalid")).getMessage());
    }
    @Test void recordedAccountVisitAppointmentAndDiagnosticCategoriesUseExistingQueryBoundary() {
        when(reader.list(any())).thenReturn(new ActivityLogRepository.Page(List.of(), 0, 20, 0, 0));
        for (String category : List.of("ACCOUNT", "VISIT", "APPOINTMENT", "DIAGNOSTIC")) {
            handler.list("", "", category, "target", "actor", 0, 20);
            verify(reader).list(argThat(c -> c.category().equals(category) && c.record().equals("target") && c.actor().equals("actor")));
        }
    }

}
