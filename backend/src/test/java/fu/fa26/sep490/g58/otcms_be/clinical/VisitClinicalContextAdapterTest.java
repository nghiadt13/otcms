package fu.fa26.sep490.g58.otcms_be.clinical;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import fu.fa26.sep490.g58.otcms_be.clinical.integration.visit.VisitClinicalContextAdapter;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitClinicalCareContextService;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitClinicalCareContextService.ClinicalCareContext;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VisitClinicalContextAdapterTest {
    private final VisitClinicalCareContextService visits = mock(VisitClinicalCareContextService.class);
    private final VisitClinicalContextAdapter adapter = new VisitClinicalContextAdapter(visits);
    private final UUID visitId = UUID.randomUUID(), patientId = UUID.randomUUID(), doctorId = UUID.randomUUID();

    @Test void absentVisitRemainsNullClinicalContext() {
        when(visits.findClinicalCareContext(visitId)).thenReturn(Optional.empty());
        assertNull(adapter.getVisitContext(visitId));
    }

    @Test void mapsPublicVisitFactsWithoutTranslatingLifecycle() {
        when(visits.findClinicalCareContext(visitId)).thenReturn(Optional.of(new ClinicalCareContext(
                visitId, patientId, UUID.randomUUID(), doctorId, true, true)));
        var context = adapter.getVisitContext(visitId);
        assertEquals(visitId, context.visitId());
        assertEquals(patientId, context.patientId());
        assertEquals(doctorId, context.responsibleDoctorId());
        assertTrue(context.clinicalWorkAllowed());
        verify(visits).findClinicalCareContext(visitId);
        verifyNoMoreInteractions(visits);
    }

    @Test void nullDoctorAndFalseEligibilityArePreserved() {
        when(visits.findClinicalCareContext(visitId)).thenReturn(Optional.of(new ClinicalCareContext(
                visitId, patientId, null, null, true, false)));
        var context = adapter.getVisitContext(visitId);
        assertNull(context.responsibleDoctorId());
        assertFalse(context.clinicalWorkAllowed());
    }

    @Test void visitDependencyFailurePropagates() {
        var failure = new IllegalStateException("Visit query unavailable");
        when(visits.findClinicalCareContext(visitId)).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> adapter.getVisitContext(visitId)));
    }
}
