package fu.fa26.sep490.g58.otcms_be.staff;

import fu.fa26.sep490.g58.otcms_be.staff.repository.StaffProfileRepository;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffProfileAccountQueryService;
import fu.fa26.sep490.g58.otcms_be.staff.service.impl.StaffProfileAccountQueryServiceImpl;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Kan27StaffProfileAccountQueryTest {
    @Test void batchProjectionExposesOnlyIdAndDisplayNameAndSkipsEmptyQueries() {
        var repository = mock(StaffProfileRepository.class);
        var service = new StaffProfileAccountQueryServiceImpl(repository);
        assertTrue(service.findByAccountIds(Set.of()).isEmpty());
        verifyNoInteractions(repository);
        UUID id = UUID.randomUUID(), accountId = UUID.randomUUID(), missingId = UUID.randomUUID();
        var row = mock(StaffProfileRepository.AccountSummary.class);
        when(row.getAccountId()).thenReturn(accountId);
        when(row.getId()).thenReturn(id);
        when(row.getDisplayName()).thenReturn("Summary label");
        when(repository.findAccountSummaries(Set.of(accountId, missingId))).thenReturn(List.of(row));
        var result = service.findByAccountIds(Set.of(accountId, missingId));
        assertEquals(Map.of(accountId, new StaffProfileAccountQueryService.StaffProfileSummary(id, "Summary label")), result);
        verify(repository).findAccountSummaries(Set.of(accountId, missingId));
        assertEquals(Set.of("id", "displayName"), Arrays.stream(StaffProfileAccountQueryService.StaffProfileSummary.class
                .getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).collect(java.util.stream.Collectors.toSet()));
    }
}
