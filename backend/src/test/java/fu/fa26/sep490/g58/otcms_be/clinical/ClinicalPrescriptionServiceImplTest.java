package fu.fa26.sep490.g58.otcms_be.clinical;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalPrescriptionStatus;
import fu.fa26.sep490.g58.otcms_be.clinical.model.entity.*;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.*;
import fu.fa26.sep490.g58.otcms_be.clinical.service.*;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalPrescriptionService.*;
import fu.fa26.sep490.g58.otcms_be.clinical.service.impl.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

/** CAS repository doubles verify service control flow; SQL races are covered separately by PostgreSQL tests. */
class ClinicalPrescriptionServiceImplTest {
    final VisitClinicalContextPort visits = mock(VisitClinicalContextPort.class);
    final ClinicalPrescriptionRepository prescriptions = mock(ClinicalPrescriptionRepository.class);
    final ClinicalPrescriptionItemRepository items = mock(ClinicalPrescriptionItemRepository.class);
    final UUID visitId = UUID.randomUUID(), patientId = UUID.randomUUID(), doctorId = UUID.randomUUID();
    final Instant now = Instant.parse("2026-11-04T10:30:00Z");
    final ClinicalPrescriptionService service = new ClinicalPrescriptionServiceImpl(visits, prescriptions, items, Clock.fixed(now, ZoneOffset.UTC));
    final ClinicalPrescriptionHistoryQueryService history = new ClinicalPrescriptionHistoryQueryServiceImpl(prescriptions, items);
    final Map<UUID, Row> rows = new ConcurrentHashMap<>();
    final Map<UUID, List<ClinicalPrescriptionItemJpaEntity>> lines = new ConcurrentHashMap<>();

    @BeforeEach void setUp() {
        when(visits.getVisitContext(any())).thenAnswer(call -> new VisitClinicalContextPort.ClinicalVisitContext(call.getArgument(0), patientId, doctorId, true));
        when(prescriptions.findByVisitId(any())).thenAnswer(call -> {
            synchronized (rows) { return Optional.ofNullable(rows.get(call.getArgument(0))).map(Row::snapshot); }
        });
        when(prescriptions.insertDraft(any(), any(), any(), any(), any())).thenAnswer(call -> {
            synchronized (rows) {
                UUID visit = call.getArgument(1); if (rows.containsKey(visit)) return 0;
                rows.put(visit, new Row(call.getArgument(0), visit, call.getArgument(3))); return 1;
            }
        });
        when(prescriptions.advanceDraftVersion(any(), anyLong(), any(), any())).thenAnswer(call -> {
            synchronized (rows) {
                var row = byId(call.getArgument(0));
                if (row.version != (long) call.getArgument(1) || row.status != ClinicalPrescriptionStatus.DRAFT) return 0;
                row.author = call.getArgument(2); row.version++; return 1;
            }
        });
        when(prescriptions.issueDraft(any(), anyLong(), anyString(), any(), any())).thenAnswer(call -> {
            synchronized (rows) {
                var row = byId(call.getArgument(0));
                if (row.version != (long) call.getArgument(1) || row.status != ClinicalPrescriptionStatus.DRAFT) return 0;
                row.key = call.getArgument(2); row.expected = call.getArgument(1); row.author = call.getArgument(3); row.issued = call.getArgument(4);
                row.status = ClinicalPrescriptionStatus.ISSUED; row.version++; return 1;
            }
        });
        when(items.deleteItems(any())).thenAnswer(call -> { var removed = lines.remove(call.getArgument(0)); return removed == null ? 0 : removed.size(); });
        when(items.saveAllAndFlush(anyList())).thenAnswer(call -> {
            List<ClinicalPrescriptionItemJpaEntity> saved = call.getArgument(0);
            lines.put(saved.getFirst().getPrescriptionId(), List.copyOf(saved)); return saved;
        });
        when(items.findByPrescriptionIdOrderByLineNumberAsc(any())).thenAnswer(call -> lines.getOrDefault(call.getArgument(0), List.of()));
        when(prescriptions.findByPatientProfileIdAndStatusOrderByIssuedAtDescIdDesc(any(), any())).thenAnswer(call ->
                ordered(call.getArgument(1)).stream().filter(row -> patientId.equals(call.getArgument(0))).map(Row::snapshot).toList());
        when(prescriptions.findByVisitIdAndStatusOrderByIssuedAtDescIdDesc(any(), any())).thenAnswer(call ->
                ordered(call.getArgument(1)).stream().filter(row -> row.visit.equals(call.getArgument(0))).map(Row::snapshot).toList());
    }
    MedicineItem medicine(String name) { return new MedicineItem(name, "1 viên", "2 lần/ngày", "Uống sau ăn"); }
    DraftCommand draft(long version, String name) { return new DraftCommand(visitId, doctorId, version, List.of(medicine(name))); }
    IssuanceCommand issuance(long version, String key) { return new IssuanceCommand(visitId, doctorId, version, key); }
    Row byId(UUID id) { return rows.values().stream().filter(row -> row.id.equals(id)).findFirst().orElseThrow(); }
    List<Row> ordered(ClinicalPrescriptionStatus status) {
        return rows.values().stream().filter(row -> row.status == status)
                .sorted(Comparator.comparing((Row row) -> row.issued, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(row -> row.id, Comparator.reverseOrder())).toList();
    }
    void error(String code, org.junit.jupiter.api.function.Executable action) {
        assertEquals(code, assertThrows(ClinicalOperationException.class, action).code());
    }

    @Test void createsReadsAndAtomicallyReplacesOrderedDraftItemsWithAdvancingVersion() {
        var first = service.saveDraft(new DraftCommand(visitId, doctorId, 0L, List.of(medicine("A"), medicine("B"))));
        assertEquals(1, first.version()); assertNull(first.issuedAt()); assertEquals(ClinicalPrescriptionStatus.DRAFT, first.status());
        assertEquals(List.of(1, 2), first.items().stream().map(PrescriptionLine::lineNumber).toList());
        assertEquals(List.of("A", "B"), first.items().stream().map(PrescriptionLine::medicine).toList());
        assertEquals(first, service.getPrescription(visitId, doctorId));
        var updated = service.saveDraft(draft(1, "C"));
        assertEquals(first.id(), updated.id()); assertEquals(2, updated.version()); assertEquals(1, updated.items().size());
        assertEquals("C", updated.items().getFirst().medicine()); assertEquals(1, rows.size());
        assertThrows(UnsupportedOperationException.class, updated.items()::clear);
        var order = inOrder(prescriptions, items); order.verify(prescriptions).advanceDraftVersion(eq(first.id()), eq(1L), eq(doctorId), any());
        order.verify(items).deleteItems(first.id());
    }

    @Test void createOnlyVersionZeroAndStaleUpdatesCannotOverwriteDraft() {
        service.saveDraft(draft(0, "A"));
        error("MSG-42", () -> service.saveDraft(draft(0, "B")));
        service.saveDraft(draft(1, "B")); clearInvocations(items);
        error("MSG-42", () -> service.saveDraft(draft(1, "C"))); verifyNoInteractions(items);
    }

    @ParameterizedTest @ValueSource(strings = {"medicine", "dose", "frequency", "usage"})
    void everyBlankMedicineFieldIsRejectedBeforePersistence(String field) {
        var line = new MedicineItem(field.equals("medicine") ? " \t" : "A", field.equals("dose") ? " " : "1",
                field.equals("frequency") ? "\n" : "daily", field.equals("usage") ? " " : "after food");
        error("MSG-39", () -> service.saveDraft(new DraftCommand(visitId, doctorId, 0L, List.of(line))));
        verifyNoInteractions(prescriptions, items);
    }

    @Test void nullEmptyAndNullElementListsAreRejected() {
        for (List<MedicineItem> list : Arrays.<List<MedicineItem>>asList(null, List.of(), Arrays.asList((MedicineItem) null)))
            error("MSG-39", () -> service.saveDraft(new DraftCommand(visitId, doctorId, 0L, list)));
        verifyNoInteractions(prescriptions, items);
    }

    @Test void maximumLengthsAndVersionValidationAreEnforcedInDomain() {
        error("MSG-39", () -> service.saveDraft(new DraftCommand(visitId, doctorId, 0L, List.of(medicine("x".repeat(256))))));
        for (Long version : Arrays.asList(null, -1L, Long.MAX_VALUE))
            error("MSG-39", () -> service.saveDraft(new DraftCommand(visitId, doctorId, version, List.of(medicine("A")))));
        error("MSG-39", () -> service.saveDraft(null)); error("MSG-39", () -> service.issue(null));
    }

    @Test void wrongOrNullDoctorAndMissingVisitAreRejectedBeforeWrites() {
        error("MSG-40", () -> service.saveDraft(new DraftCommand(visitId, UUID.randomUUID(), 0L, List.of(medicine("A")))));
        error("MSG-40", () -> service.getPrescription(visitId, null));
        when(visits.getVisitContext(visitId)).thenReturn(null);
        error("MSG-41", () -> service.saveDraft(draft(0, "A"))); error("MSG-41", () -> service.getPrescription(visitId, doctorId));
        error("MSG-39", () -> service.getPrescription(null, doctorId)); verifyNoInteractions(prescriptions, items);
    }

    @Test void closedOrDisallowedCareDeniesGetWriteAndIssue() {
        when(visits.getVisitContext(visitId)).thenReturn(new VisitClinicalContextPort.ClinicalVisitContext(visitId, patientId, doctorId, false));
        error("MSG-42", () -> service.saveDraft(draft(0, "A"))); error("MSG-42", () -> service.issue(issuance(1, "key")));
        error("MSG-42", () -> service.getPrescription(visitId, doctorId)); verifyNoInteractions(prescriptions, items);
    }

    @Test void missingPatientAndMismatchedVisitContextAreRejected() {
        when(visits.getVisitContext(visitId)).thenReturn(new VisitClinicalContextPort.ClinicalVisitContext(visitId, null, doctorId, true));
        error("MSG-39", () -> service.saveDraft(draft(0, "A")));
        when(visits.getVisitContext(visitId)).thenReturn(new VisitClinicalContextPort.ClinicalVisitContext(UUID.randomUUID(), patientId, doctorId, true));
        error("MSG-39", () -> service.getPrescription(visitId, doctorId)); verifyNoInteractions(prescriptions, items);
    }

    @Test void missingPrescriptionAndInvalidIssuanceKeyAreRejected() {
        error("MSG-41", () -> service.issue(issuance(1, "key")));
        for (String key : Arrays.asList(null, " ", "x".repeat(129))) error("MSG-39", () -> service.issue(issuance(1, key)));
        error("MSG-39", () -> service.issue(issuance(0, "key")));
    }

    @Test void issueSetsServerTimestampAndIssuedContentIsImmutableWithSafeReplay() {
        var draft = service.saveDraft(draft(0, "A"));
        var issued = service.issue(issuance(draft.version(), " key "));
        assertEquals(now, issued.issuedAt()); assertEquals(2, issued.version()); assertEquals(ClinicalPrescriptionStatus.ISSUED, issued.status());
        clearInvocations(prescriptions, items);
        assertEquals(issued, service.issue(issuance(1, "key")));
        verify(prescriptions, never()).issueDraft(any(), anyLong(), any(), any(), any());
        verify(items, never()).deleteItems(any()); verify(items, never()).saveAllAndFlush(anyList());
        error("IDEMPOTENCY_CONFLICT", () -> service.issue(issuance(2, "key")));
        error("MSG-42", () -> service.issue(issuance(1, "another")));
        error("MSG-42", () -> service.saveDraft(draft(2, "B")));
        error("MSG-42", () -> service.saveDraft(draft(0, "B")));
        assertEquals(issued, service.getPrescription(visitId, doctorId));
    }

    @Test void issuanceRequiresValidSavedLinesAndCorrectVersionButNotAnExamination() {
        var saved = service.saveDraft(draft(0, "A"));
        error("MSG-42", () -> service.issue(issuance(2, "key")));
        lines.put(saved.id(), List.of()); error("MSG-39", () -> service.issue(issuance(1, "key")));
        verify(prescriptions, never()).issueDraft(any(), anyLong(), any(), any(), any());
    }

    @Test void patientLinkageIsFixedAndCurrentResponsibleDoctorIsRecordedAtIssuance() {
        service.saveDraft(draft(0, "A"));
        when(visits.getVisitContext(visitId)).thenReturn(new VisitClinicalContextPort.ClinicalVisitContext(visitId, UUID.randomUUID(), doctorId, true));
        error("MSG-41", () -> service.issue(issuance(1, "key")));
        UUID other = UUID.randomUUID();
        when(visits.getVisitContext(visitId)).thenReturn(new VisitClinicalContextPort.ClinicalVisitContext(visitId, patientId, other, true));
        assertEquals(doctorId, service.getPrescription(visitId, other).doctorProfileId());
        var issued = service.issue(new IssuanceCommand(visitId, other, 1L, "key"));
        assertEquals(other, issued.doctorProfileId());
        error("MSG-40", () -> service.getPrescription(visitId, doctorId));
        when(visits.getVisitContext(visitId)).thenReturn(new VisitClinicalContextPort.ClinicalVisitContext(visitId, patientId, doctorId, true));
        error("MSG-42", () -> service.issue(issuance(1, "key")));
        assertEquals(other, service.getPrescription(visitId, doctorId).doctorProfileId());
    }

    @Test void historyReturnsOnlyIssuedImmutableDataInDeterministicOrderWithoutVisitAuthorization() {
        service.saveDraft(draft(0, "A"));
        assertTrue(history.findIssuedByPatient(patientId).isEmpty()); assertTrue(history.findIssuedByVisit(visitId).isEmpty());
        var issued = service.issue(issuance(1, "key"));
        UUID anotherVisit = UUID.randomUUID();
        service.saveDraft(new DraftCommand(anotherVisit, doctorId, 0L, List.of(medicine("B"))));
        service.issue(new IssuanceCommand(anotherVisit, doctorId, 1L, "other"));
        clearInvocations(visits);
        var result = history.findIssuedByPatient(patientId);
        assertEquals(ordered(ClinicalPrescriptionStatus.ISSUED).stream().map(row -> row.id).toList(), result.stream().map(value -> value.id()).toList());
        assertEquals(issued.items(), history.findIssuedByVisit(visitId).getFirst().items());
        assertThrows(UnsupportedOperationException.class, result::clear);
        assertThrows(UnsupportedOperationException.class, result.getFirst().items()::clear); verifyNoInteractions(visits);
        error("MSG-39", () -> history.findIssuedByPatient(null)); error("MSG-39", () -> history.findIssuedByVisit(null));
    }

    @Test void twoDraftUpdatesWithSameVersionHaveOneWinner() throws Exception {
        service.saveDraft(draft(0, "initial"));
        var results = race(() -> service.saveDraft(draft(1, "A")), () -> service.saveDraft(draft(1, "B")));
        assertEquals(1, results.stream().filter(PrescriptionView.class::isInstance).count()); assertTrue(results.contains("MSG-42"));
        assertEquals(2, rows.get(visitId).version); assertEquals(1, service.getPrescription(visitId, doctorId).items().size());
    }

    @Test void twoIdenticalIssuanceAttemptsReturnSameImmutableAggregate() throws Exception {
        service.saveDraft(draft(0, "A"));
        var results = race(() -> service.issue(issuance(1, "key")), () -> service.issue(issuance(1, "key")));
        assertInstanceOf(PrescriptionView.class, results.getFirst()); assertEquals(results.getFirst(), results.getLast());
        assertEquals(1, rows.size()); assertEquals(2, rows.get(visitId).version);
    }

    @Test void issuanceVersusDraftUpdateCannotLoseOrOverwriteIssuedContent() throws Exception {
        service.saveDraft(draft(0, "A"));
        var results = race(() -> service.issue(issuance(1, "key")), () -> service.saveDraft(draft(1, "B")));
        assertEquals(1, results.stream().filter(PrescriptionView.class::isInstance).count()); assertTrue(results.contains("MSG-42"));
        var current = service.getPrescription(visitId, doctorId);
        assertEquals(2, current.version()); assertEquals(1, current.items().size());
        assertEquals(current.status() == ClinicalPrescriptionStatus.ISSUED ? "A" : "B", current.items().getFirst().medicine());
    }

    private List<Object> race(Callable<PrescriptionView> first, Callable<PrescriptionView> second) throws Exception {
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Object> a = () -> { barrier.await(5, TimeUnit.SECONDS); return outcome(first); };
            Callable<Object> b = () -> { barrier.await(5, TimeUnit.SECONDS); return outcome(second); };
            var x = executor.submit(a); var y = executor.submit(b); return List.of(x.get(10, TimeUnit.SECONDS), y.get(10, TimeUnit.SECONDS));
        }
    }
    private Object outcome(Callable<PrescriptionView> action) throws Exception { try { return action.call(); } catch (ClinicalOperationException failure) { return failure.code(); } }
    private class Row {
        final UUID id, visit; long version = 1; ClinicalPrescriptionStatus status = ClinicalPrescriptionStatus.DRAFT;
        Instant issued; String key; Long expected; UUID author;
        Row(UUID id, UUID visit, UUID author) { this.id = id; this.visit = visit; this.author = author; }
        ClinicalPrescriptionJpaEntity snapshot() {
            var row = mock(ClinicalPrescriptionJpaEntity.class);
            when(row.getId()).thenReturn(id); when(row.getVisitId()).thenReturn(visit);
            when(row.getPatientProfileId()).thenReturn(patientId); when(row.getDoctorProfileId()).thenReturn(author);
            when(row.getVersion()).thenReturn(version); when(row.getStatus()).thenReturn(status);
            when(row.getIssuedAt()).thenReturn(issued); when(row.getIssuanceKey()).thenReturn(key);
            when(row.getIssuanceExpectedVersion()).thenReturn(expected); return row;
        }
    }
}
