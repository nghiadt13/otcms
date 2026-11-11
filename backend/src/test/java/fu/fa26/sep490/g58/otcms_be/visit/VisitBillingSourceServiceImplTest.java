package fu.fa26.sep490.g58.otcms_be.visit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.model.ExamQueueEntry;
import fu.fa26.sep490.g58.otcms_be.visit.model.VisitExamStatus;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.ClinicVisitJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.repository.ClinicVisitJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitBillingSourceService.BillingSource;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitExamQueueService;
import fu.fa26.sep490.g58.otcms_be.visit.service.impl.VisitBillingSourceServiceImpl;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class VisitBillingSourceServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-11-11T03:00:00Z");
    private static final UUID VISIT = UUID.fromString("7e7e7e7e-8f8f-4a0a-9b1b-2c2c2c2c2c2c");
    private static final UUID PATIENT = UUID.fromString("3a3a3a3a-4b4b-4c5c-8d6d-7e7e7e7e7e7e");
    private static final UUID FEE_SERVICE = UUID.fromString("5f5f5f5f-6a6a-4b7b-8c8c-9d9d9d9d9d9d");

    private final ClinicVisitJpaRepository visits = mock(ClinicVisitJpaRepository.class);
    private final VisitExamQueueService examQueue = mock(VisitExamQueueService.class);
    private final VisitBillingSourceServiceImpl service = new VisitBillingSourceServiceImpl(visits, examQueue);

    @Test
    void openWalkInAwaitingItsFeeIsBillableOnlyForTheFeeService() {
        givenVisit("OPEN");
        when(examQueue.requiredFeeServiceId(VISIT)).thenReturn(Optional.of(FEE_SERVICE));

        BillingSource source = service.requireBillableVisit(VISIT);

        assertEquals(new BillingSource(VISIT, PATIENT, FEE_SERVICE), source);
    }

    @Test
    void openVisitWithoutPendingFeeIsNotBillable() {
        givenVisit("OPEN");
        when(examQueue.requiredFeeServiceId(VISIT)).thenReturn(Optional.empty());

        assertNotBillable();
    }

    @Test
    void closedVisitWithoutQueueRowIsBillableWithoutServiceRestriction() {
        givenVisit("CLOSED");
        when(examQueue.find(VISIT)).thenReturn(Optional.empty());

        assertEquals(new BillingSource(VISIT, PATIENT, null), service.requireBillableVisit(VISIT));
    }

    @Test
    void closedWalkInThatCompletedIsBillable() {
        givenVisit("CLOSED");
        when(examQueue.find(VISIT)).thenReturn(Optional.of(entry(VisitExamStatus.COMPLETED)));

        assertEquals(new BillingSource(VISIT, PATIENT, null), service.requireBillableVisit(VISIT));
    }

    @ParameterizedTest
    @EnumSource(value = VisitExamStatus.class, names = {"PENDING_PAYMENT", "CANCELLED"})
    void closedVisitThatWasCancelledOrNeverPaidIsNotBillable(VisitExamStatus status) {
        givenVisit("CLOSED");
        when(examQueue.find(VISIT)).thenReturn(Optional.of(entry(status)));

        assertNotBillable();
    }

    @Test
    void missingVisitIsNotBillable() {
        when(visits.findById(VISIT)).thenReturn(Optional.empty());

        assertNotBillable();
    }

    private void givenVisit(String status) {
        var visit = mock(ClinicVisitJpaEntity.class);
        when(visit.getId()).thenReturn(VISIT);
        when(visit.getPatientProfileId()).thenReturn(PATIENT);
        when(visit.getStatus()).thenReturn(status);
        when(visits.findById(VISIT)).thenReturn(Optional.of(visit));
    }

    private void assertNotBillable() {
        assertEquals("VISIT_NOT_BILLABLE",
                assertThrows(VisitOperationException.class, () -> service.requireBillableVisit(VISIT)).code());
    }

    private static ExamQueueEntry entry(VisitExamStatus status) {
        return new ExamQueueEntry(VISIT, UUID.randomUUID(), null, FEE_SERVICE,
                status == VisitExamStatus.PENDING_PAYMENT ? null : 1, status, LocalDate.of(2026, 11, 11), NOW,
                null, null, null);
    }
}
