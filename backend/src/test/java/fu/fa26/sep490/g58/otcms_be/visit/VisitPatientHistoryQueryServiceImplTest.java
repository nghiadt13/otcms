package fu.fa26.sep490.g58.otcms_be.visit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.ClinicVisitJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.repository.ClinicVisitJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.impl.VisitPatientHistoryQueryServiceImpl;
import java.util.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;

class VisitPatientHistoryQueryServiceImplTest {
    final ClinicVisitJpaRepository visits = mock(ClinicVisitJpaRepository.class);
    final VisitPatientHistoryQueryServiceImpl service = new VisitPatientHistoryQueryServiceImpl(visits);
    final UUID patient = UUID.randomUUID(), current = UUID.randomUUID();
    @Test void missingCurrentVisitIsUnavailable() {
        assertEquals("RESOURCE_NOT_FOUND", assertThrows(VisitOperationException.class, () -> service.findPastVisits(patient, current, 0, 20)).code());
    }
    @Test void differentPatientCannotSelectHistory() {
        when(visits.findById(current)).thenReturn(Optional.of(ClinicVisitJpaEntity.walkIn(current, UUID.randomUUID(), null, UUID.randomUUID(), Instant.EPOCH)));
        assertEquals("ACCESS_DENIED", assertThrows(VisitOperationException.class, () -> service.findPastVisits(patient, current, 0, 20)).code());
        verify(visits, never()).findHistoricalVisits(any(), any(), any(), any());
    }
    @Test void pageMapsVisitFactsAndRepositoryPreservesStableOrderAndTotal() {
        var currentRow = ClinicVisitJpaEntity.walkIn(current, patient, null, UUID.randomUUID(), Instant.EPOCH.plusSeconds(100));
        when(visits.findById(current)).thenReturn(Optional.of(currentRow));
        var newer = mock(ClinicVisitJpaEntity.class); var older = mock(ClinicVisitJpaEntity.class);
        when(newer.getId()).thenReturn(UUID.randomUUID()); when(older.getId()).thenReturn(UUID.randomUUID());
        for (var row : List.of(newer, older)) { when(row.getPatientProfileId()).thenReturn(patient); when(row.getStatus()).thenReturn("CLOSED"); }
        when(newer.getArrivedAt()).thenReturn(Instant.EPOCH.plusSeconds(10)); when(older.getArrivedAt()).thenReturn(Instant.EPOCH);
        when(visits.findHistoricalVisits(patient, current, currentRow.getArrivedAt(), PageRequest.of(1, 2)))
                .thenReturn(new PageImpl<>(List.of(newer, older), PageRequest.of(1, 2), 9));
        var result = service.findPastVisits(patient, current, 1, 2);
        assertEquals(9, result.total()); assertEquals(List.of(newer.getId(), older.getId()), result.items().stream().map(v -> v.visitId()).toList());
        verify(visits, never()).save(any()); assertThrows(UnsupportedOperationException.class, () -> result.items().clear());
    }
    @Test void invalidPagingDoesNotTouchPersistence() {
        assertThrows(VisitOperationException.class, () -> service.findPastVisits(patient, current, -1, 20));
        assertThrows(VisitOperationException.class, () -> service.findPastVisits(patient, current, 0, 51)); verifyNoInteractions(visits);
    }
}
