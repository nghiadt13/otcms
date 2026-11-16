package fu.fa26.sep490.g58.otcms_be.visit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import fu.fa26.sep490.g58.otcms_be.visit.model.entity.ClinicVisitJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.VisitCareAssignmentJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.repository.ClinicVisitJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitCareAssignmentRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.impl.VisitClinicalCareContextServiceImpl;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.parser.PartTree;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

class VisitClinicalCareContextServiceImplTest {
    private final ClinicVisitJpaRepository visits = mock(ClinicVisitJpaRepository.class);
    private final VisitCareAssignmentRepository assignments = mock(VisitCareAssignmentRepository.class);
    private final VisitClinicalCareContextServiceImpl service = new VisitClinicalCareContextServiceImpl(visits, assignments);
    private final UUID visitId = UUID.randomUUID(), patientId = UUID.randomUUID(), doctorId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-11-04T10:00:00Z");

    @BeforeEach void setUp() {
        when(visits.findById(visitId)).thenReturn(Optional.of(openVisit()));
        when(assignments.findFirstByVisitIdAndStatusOrderByAssignedAtDescIdDesc(visitId, "IN_PROGRESS"))
                .thenReturn(Optional.empty());
    }

    @Test void nullIdReturnsEmptyWithoutPersistence() {
        assertTrue(service.findClinicalCareContext(null).isEmpty());
        verifyNoInteractions(visits, assignments);
    }

    @Test void missingVisitReturnsEmptyWithoutAssignmentQuery() {
        when(visits.findById(visitId)).thenReturn(Optional.empty());
        assertTrue(service.findClinicalCareContext(visitId).isEmpty());
        verifyNoInteractions(assignments);
    }

    @Test void openVisitAndActualCareExposeOnlyOwnedFactsWithoutWrites() {
        var assignment = inProgress(UUID.randomUUID(), doctorId, now);
        when(assignments.findFirstByVisitIdAndStatusOrderByAssignedAtDescIdDesc(visitId, "IN_PROGRESS"))
                .thenReturn(Optional.of(assignment));
        var context = service.findClinicalCareContext(visitId).orElseThrow();
        assertEquals(visitId, context.visitId());
        assertEquals(patientId, context.patientProfileId());
        assertEquals(assignment.getId(), context.activeCareAssignmentId());
        assertEquals(doctorId, context.responsibleDoctorProfileId());
        assertTrue(context.visitOpen());
        assertTrue(context.clinicalWorkAllowed());
        verify(visits).findById(visitId);
        verify(assignments).findFirstByVisitIdAndStatusOrderByAssignedAtDescIdDesc(visitId, "IN_PROGRESS");
        verifyNoMoreInteractions(visits, assignments);
    }

    @Test void openWithoutStartedCareIsNotClinicallyWorkable() {
        var context = service.findClinicalCareContext(visitId).orElseThrow();
        assertTrue(context.visitOpen());
        assertFalse(context.clinicalWorkAllowed());
        assertNull(context.activeCareAssignmentId());
        assertNull(context.responsibleDoctorProfileId());
    }

    @ParameterizedTest @ValueSource(strings = {"RESERVED", "COMPLETED", "RELEASED"})
    void otherCareStatusesCannotBeUsedAsCurrentTreatingDoctor(String excludedStatus) {
        var excluded = inProgress(UUID.randomUUID(), doctorId, now);
        ReflectionTestUtils.setField(excluded, "status", excludedStatus);
        when(assignments.findFirstByVisitIdAndStatusOrderByAssignedAtDescIdDesc(eq(visitId), anyString()))
                .thenAnswer(invocation -> excludedStatus.equals(invocation.getArgument(1))
                        ? Optional.of(excluded) : Optional.empty());
        var context = service.findClinicalCareContext(visitId).orElseThrow();
        assertFalse(context.clinicalWorkAllowed());
        assertNull(context.responsibleDoctorProfileId());
        verify(assignments).findFirstByVisitIdAndStatusOrderByAssignedAtDescIdDesc(visitId, "IN_PROGRESS");
        verifyNoMoreInteractions(assignments);
    }

    @Test void closedVisitIsNotWorkableEvenWithStaleInProgressAssignment() {
        var closed = openVisit();
        ReflectionTestUtils.setField(closed, "status", "CLOSED");
        when(visits.findById(visitId)).thenReturn(Optional.of(closed));
        var assignment = inProgress(UUID.randomUUID(), doctorId, now);
        when(assignments.findFirstByVisitIdAndStatusOrderByAssignedAtDescIdDesc(visitId, "IN_PROGRESS"))
                .thenReturn(Optional.of(assignment));
        var context = service.findClinicalCareContext(visitId).orElseThrow();
        assertFalse(context.visitOpen());
        assertFalse(context.clinicalWorkAllowed());
        assertEquals(doctorId, context.responsibleDoctorProfileId());
        assertEquals(assignment.getId(), context.activeCareAssignmentId());
    }

    @Test void malformedAssignmentWithoutDoctorDoesNotAuthorizeClinicalWork() {
        when(assignments.findFirstByVisitIdAndStatusOrderByAssignedAtDescIdDesc(visitId, "IN_PROGRESS"))
                .thenReturn(Optional.of(inProgress(UUID.randomUUID(), null, now)));
        assertFalse(service.findClinicalCareContext(visitId).orElseThrow().clinicalWorkAllowed());
    }

    @Test void derivedQueryLimitsSelectionAndOrdersByAssignedTimeThenIdDeterministically() {
        String method = "findFirstByVisitIdAndStatusOrderByAssignedAtDescIdDesc";
        var query = new PartTree(method, VisitCareAssignmentJpaEntity.class);
        assertTrue(query.isLimiting());
        assertEquals(1, query.getMaxResults());
        assertEquals(Sort.by(Sort.Order.desc("assignedAt"), Sort.Order.desc("id")), query.getSort());
        assertEquals(List.of("visitId", "status"), query.getParts().stream()
                .map(part -> part.getProperty().toDotPath()).toList());
        // Unit validation proves query parsing/order/limit, not actual PostgreSQL row ordering.
        var selected = inProgress(UUID.randomUUID(), doctorId, now);
        when(assignments.findFirstByVisitIdAndStatusOrderByAssignedAtDescIdDesc(visitId, "IN_PROGRESS"))
                .thenReturn(Optional.of(selected));
        assertEquals(selected.getId(), service.findClinicalCareContext(visitId).orElseThrow().activeCareAssignmentId());
    }

    @Test void infrastructureFailureIsNotConvertedIntoAbsentVisit() {
        var failure = new IllegalStateException("Visit read unavailable");
        when(visits.findById(visitId)).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> service.findClinicalCareContext(visitId)));
    }

    @Test void publicReadImplementationIsReadOnly() {
        assertTrue(VisitClinicalCareContextServiceImpl.class.getAnnotation(Transactional.class).readOnly());
    }

    @Test void batchContextsUseTwoBulkReadsAndPreserveNewestCurrentCare() {
        var newer = inProgress(UUID.randomUUID(), doctorId, now);
        var older = inProgress(UUID.randomUUID(), UUID.randomUUID(), now.minusSeconds(60));
        when(visits.findAllById(List.of(visitId))).thenReturn(List.of(openVisit()));
        when(assignments.findByVisitIdInAndStatusOrderByAssignedAtDescIdDesc(List.of(visitId), "IN_PROGRESS")).thenReturn(List.of(newer, older));
        var context = service.findClinicalCareContexts(List.of(visitId)).get(visitId);
        assertEquals(newer.getId(), context.activeCareAssignmentId()); assertEquals(doctorId, context.responsibleDoctorProfileId());
        assertTrue(context.clinicalWorkAllowed());
        verify(visits).findAllById(List.of(visitId));
        verify(assignments).findByVisitIdInAndStatusOrderByAssignedAtDescIdDesc(List.of(visitId), "IN_PROGRESS");
        verify(visits, never()).findById(any());
        verify(assignments, never()).findFirstByVisitIdAndStatusOrderByAssignedAtDescIdDesc(any(), any());
    }

    private ClinicVisitJpaEntity openVisit() {
        return ClinicVisitJpaEntity.walkIn(visitId, patientId, null, UUID.randomUUID(), now, null, null);
    }

    private VisitCareAssignmentJpaEntity inProgress(UUID id, UUID doctor, Instant assignedAt) {
        return VisitCareAssignmentJpaEntity.inProgress(id, visitId, doctor, "EXAMINATION", 30,
                assignedAt, assignedAt.plusSeconds(1800));
    }
}
