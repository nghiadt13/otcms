package fu.fa26.sep490.g58.otcms_be.visit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitCareAssignmentRepository;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitCareAssignmentRepository.DoctorActiveCareRow;
import fu.fa26.sep490.g58.otcms_be.visit.service.impl.VisitDoctorActiveCareReadServiceImpl;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

class VisitDoctorActiveCareReadServiceImplTest {
    private final VisitCareAssignmentRepository assignments = mock(VisitCareAssignmentRepository.class);
    private final VisitDoctorActiveCareReadServiceImpl service = new VisitDoctorActiveCareReadServiceImpl(assignments);
    private final UUID doctorId = UUID.randomUUID(), patientId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-11-04T14:00:00Z");

    @Test void scheduledVisitMapsAllCurrentCareFactsWithoutWriting() {
        var row = row("SCHEDULED", UUID.randomUUID(), now);
        when(assignments.findDoctorActiveCare(doctorId)).thenReturn(List.of(row));
        var result = service.findActiveVisits(doctorId).getFirst();
        assertEquals(row.getVisitId(), result.visitId());
        assertEquals(patientId, result.patientProfileId());
        assertEquals("SCHEDULED", result.visitSource());
        assertEquals(row.getLinkedAppointmentId(), result.linkedAppointmentId());
        assertEquals(row.getAssignmentId(), result.assignmentId());
        assertEquals("SCHEDULED_APPOINTMENT", result.stepCode());
        assertEquals(now, result.startedAt());
        assertEquals(now.plusSeconds(1800), result.expectedEnd());
        verify(assignments).findDoctorActiveCare(doctorId); verifyNoMoreInteractions(assignments);
    }

    @Test void walkInVisitHasNoInventedAppointmentLink() {
        var row = row("WALK_IN", null, now);
        when(assignments.findDoctorActiveCare(doctorId)).thenReturn(List.of(row));
        var result = service.findActiveVisits(doctorId).getFirst();
        assertEquals("WALK_IN", result.visitSource()); assertNull(result.linkedAppointmentId());
    }

    @Test void multipleRowsKeepRepositoryDeterministicOrderAndResultIsImmutable() {
        var first = row("WALK_IN", null, now);
        var second = row("SCHEDULED", UUID.randomUUID(), now.plusSeconds(1));
        when(assignments.findDoctorActiveCare(doctorId)).thenReturn(List.of(first, second));
        var result = service.findActiveVisits(doctorId);
        assertEquals(List.of(first.getAssignmentId(), second.getAssignmentId()), result.stream().map(value -> value.assignmentId()).toList());
        assertThrows(UnsupportedOperationException.class, result::clear);
    }

    @Test void noActiveCareReturnsEmpty() {
        when(assignments.findDoctorActiveCare(doctorId)).thenReturn(List.of());
        assertTrue(service.findActiveVisits(doctorId).isEmpty());
    }

    @Test void queryRestrictsDoctorOpenVisitAndStartedCareAndOrdersDeterministically() throws Exception {
        String query = VisitCareAssignmentRepository.class.getMethod("findDoctorActiveCare", UUID.class)
                .getAnnotation(Query.class).value().replaceAll("\\s+", " ").trim();
        assertTrue(query.contains("a.doctorProfileId = :doctorProfileId"));
        assertTrue(query.contains("a.status = 'IN_PROGRESS'"));
        assertTrue(query.contains("v.status = 'OPEN'"));
        assertTrue(query.contains("on v.id = a.visitId"));
        assertTrue(query.endsWith("order by a.startsAt asc, a.id asc"));
        for (String excluded : List.of("RESERVED", "COMPLETED", "RELEASED", "CLOSED")) assertFalse(query.contains(excluded));
        // Static query-contract coverage; real database filtering is not claimed by this unit test.
    }

    @Test void nullDoctorIsRejectedBeforePersistence() {
        assertThrows(IllegalArgumentException.class, () -> service.findActiveVisits(null));
        verifyNoInteractions(assignments);
    }

    @Test void queryFailurePropagatesWithoutFallbackData() {
        var failure = new IllegalStateException("Care read unavailable");
        when(assignments.findDoctorActiveCare(doctorId)).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> service.findActiveVisits(doctorId)));
    }

    private DoctorActiveCareRow row(String source, UUID appointmentId, Instant startedAt) {
        var row = mock(DoctorActiveCareRow.class);
        when(row.getVisitId()).thenReturn(UUID.randomUUID());
        when(row.getPatientProfileId()).thenReturn(patientId);
        when(row.getVisitSource()).thenReturn(source);
        when(row.getLinkedAppointmentId()).thenReturn(appointmentId);
        when(row.getAssignmentId()).thenReturn(UUID.randomUUID());
        when(row.getStepCode()).thenReturn(source.equals("SCHEDULED") ? "SCHEDULED_APPOINTMENT" : "EXAMINATION");
        when(row.getStartedAt()).thenReturn(startedAt);
        when(row.getExpectedEnd()).thenReturn(startedAt.plusSeconds(1800));
        return row;
    }
}
