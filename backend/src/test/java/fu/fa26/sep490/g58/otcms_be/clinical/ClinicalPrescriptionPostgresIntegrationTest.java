package fu.fa26.sep490.g58.otcms_be.clinical;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalPrescriptionStatus;
import fu.fa26.sep490.g58.otcms_be.clinical.service.*;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalPrescriptionService.*;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Dedicated local test DB only; UUID-isolated fixtures, no reset or database lifecycle commands. */
@SpringBootTest @LocalPostgresTest
class ClinicalPrescriptionPostgresIntegrationTest {
    @MockitoBean VisitClinicalContextPort visits;
    @Autowired ClinicalPrescriptionService prescriptions;
    @Autowired ClinicalPrescriptionHistoryQueryService history;
    @Autowired JdbcTemplate jdbc;
    final UUID visitId = UUID.randomUUID(), patientId = UUID.randomUUID(), doctorId = UUID.randomUUID();

    @BeforeEach void currentCare() {
        when(visits.getVisitContext(any())).thenAnswer(call -> new VisitClinicalContextPort.ClinicalVisitContext(
                call.getArgument(0), patientId, doctorId, true));
    }
    DraftCommand draft(long version, String name) { return new DraftCommand(visitId, doctorId, version,
            List.of(new MedicineItem(name, "1 viên", "2 lần/ngày", "Uống sau ăn"))); }
    IssuanceCommand issue() { return new IssuanceCommand(visitId, doctorId, 1L, "issue-key"); }

    @Test void concurrentInitialPutsPersistOneAggregateAndOneItemSet() throws Exception {
        var results = race(() -> prescriptions.saveDraft(draft(0, "A")), () -> prescriptions.saveDraft(draft(0, "B")));
        oneWinner(results); assertCounts(1); assertEquals(1, prescriptions.getPrescription(visitId, doctorId).version());
    }
    @Test void concurrentSameVersionUpdatesCannotLoseAnUpdate() throws Exception {
        prescriptions.saveDraft(draft(0, "initial"));
        oneWinner(race(() -> prescriptions.saveDraft(draft(1, "A")), () -> prescriptions.saveDraft(draft(1, "B"))));
        assertCounts(1); assertEquals(2, prescriptions.getPrescription(visitId, doctorId).version());
    }
    @Test void concurrentIdenticalIssuanceReplaysTheSameIssuedSnapshot() throws Exception {
        prescriptions.saveDraft(draft(0, "A"));
        var results = race(() -> prescriptions.issue(issue()), () -> prescriptions.issue(issue()));
        assertInstanceOf(PrescriptionView.class, results.getFirst()); assertEquals(results.getFirst(), results.getLast());
        assertEquals(ClinicalPrescriptionStatus.ISSUED, prescriptions.getPrescription(visitId, doctorId).status()); assertCounts(1);
    }
    @Test void issuanceRacingDraftReplacementHasOneTransitionAndNoPartialLines() throws Exception {
        prescriptions.saveDraft(draft(0, "A"));
        oneWinner(race(() -> prescriptions.issue(issue()), () -> prescriptions.saveDraft(draft(1, "B"))));
        var result = prescriptions.getPrescription(visitId, doctorId); assertCounts(1); assertEquals(2, result.version());
        assertEquals(result.status() == ClinicalPrescriptionStatus.ISSUED ? "A" : "B", result.items().getFirst().medicine());
    }
    @Test void issuedHistoryAndIdempotencySurviveIndependentTransactions() {
        var saved = prescriptions.saveDraft(draft(0, "A"));
        assertTrue(history.findIssuedByPatient(patientId).isEmpty());
        var issued = prescriptions.issue(issue()); assertNotNull(issued.issuedAt()); assertEquals(saved.id(), issued.id());
        assertEquals(issued, prescriptions.issue(issue())); assertCounts(1);
        assertEquals(issued.id(), history.findIssuedByVisit(visitId).getFirst().id());
        assertEquals("IDEMPOTENCY_CONFLICT", assertThrows(ClinicalOperationException.class,
                () -> prescriptions.issue(new IssuanceCommand(visitId, doctorId, 2L, "issue-key"))).code());
    }
    private void assertCounts(int expectedItems) {
        assertEquals(1, jdbc.queryForObject("select count(*) from clinical_prescriptions where visit_id = ?", Integer.class, visitId));
        assertEquals(expectedItems, jdbc.queryForObject("""
                select count(*) from clinical_prescription_items i join clinical_prescriptions p on p.id = i.prescription_id
                where p.visit_id = ?
                """, Integer.class, visitId));
    }
    private void oneWinner(List<Object> results) {
        assertEquals(1, results.stream().filter(PrescriptionView.class::isInstance).count()); assertTrue(results.contains("MSG-42"));
    }
    private List<Object> race(Callable<PrescriptionView> first, Callable<PrescriptionView> second) throws Exception {
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var x = executor.submit(() -> { barrier.await(5, TimeUnit.SECONDS); return outcome(first); });
            var y = executor.submit(() -> { barrier.await(5, TimeUnit.SECONDS); return outcome(second); });
            return List.of(x.get(30, TimeUnit.SECONDS), y.get(30, TimeUnit.SECONDS));
        }
    }
    private Object outcome(Callable<PrescriptionView> action) throws Exception {
        try { return action.call(); } catch (ClinicalOperationException failure) { return failure.code(); }
    }
}
