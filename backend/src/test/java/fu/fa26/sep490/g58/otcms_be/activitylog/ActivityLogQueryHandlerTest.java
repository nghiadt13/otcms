package fu.fa26.sep490.g58.otcms_be.activitylog;
import fu.fa26.sep490.g58.otcms_be.activitylog.application.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
class ActivityLogQueryHandlerTest {
    private final ActivityLogReader reader = mock(ActivityLogReader.class);
    private final ActivityLogQueryHandler handler = new ActivityLogQueryHandler(reader);
    @Test void rejectsInvalidDatesFiltersAndPaginationBeforeQuerying() {
        for (String[] dates : List.of(new String[]{"2026-11-07", "2026-11-01"}, new String[]{"not-a-date", ""}, new String[]{"2026-02-30", ""})) {
            assertEquals("MSG-54", assertThrows(ActivityLogFailure.class, () -> handler.list(dates[0], dates[1], "", "", "", 0, 20)).getMessage());
        }
        assertThrows(ActivityLogFailure.class, () -> handler.list("", "", "AUTH", "", "", 0, 20));
        assertThrows(ActivityLogFailure.class, () -> handler.list("", "", "", "", "", -1, 20));
        assertThrows(ActivityLogFailure.class, () -> handler.list("", "", "", "", "", 0, 101));
        verifyNoInteractions(reader);
    }
    @Test void usesInclusiveClinicDatesAndExclusiveNextDayBoundary() {
        when(reader.list(any())).thenReturn(new ActivityLogReader.Page(List.of(), 0, 20, 0, 0));
        handler.list("2026-11-01", "2026-11-01", "ROOM", "P.101", " admin ", 0, 20);
        var criteria = ArgumentCaptor.forClass(ActivityLogReader.Criteria.class);
        verify(reader).list(criteria.capture());
        assertEquals(Instant.parse("2026-10-31T17:00:00Z"), criteria.getValue().from());
        assertEquals(Instant.parse("2026-11-01T17:00:00Z"), criteria.getValue().until());
        assertEquals("admin", criteria.getValue().actor());
    }
    @Test void missingOrInvalidIdIsNotFoundWithoutLeakingInternalDetails() {
        String id = "room:11111111-1111-1111-1111-111111111111";
        when(reader.find(id)).thenReturn(Optional.empty());
        assertEquals("MSG-50", assertThrows(ActivityLogFailure.class, () -> handler.detail(id)).getMessage());
        assertEquals("MSG-50", assertThrows(ActivityLogFailure.class, () -> handler.detail("invalid")).getMessage());
    }
}
