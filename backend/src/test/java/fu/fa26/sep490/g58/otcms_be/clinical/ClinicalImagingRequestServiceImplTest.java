package fu.fa26.sep490.g58.otcms_be.clinical;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.integration.servicecatalog.ServiceCatalogClinicalAdapter;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalServiceType;
import fu.fa26.sep490.g58.otcms_be.clinical.model.entity.*;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.*;
import fu.fa26.sep490.g58.otcms_be.clinical.service.*;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalImagingRequestService.*;
import fu.fa26.sep490.g58.otcms_be.clinical.service.impl.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ClinicalImagingRequestServiceImplTest {
    private final VisitClinicalContextPort visits = mock(VisitClinicalContextPort.class);
    private final ClinicServiceCatalogPort catalog = mock(ClinicServiceCatalogPort.class);
    private final OrderingRoundRepository rounds = mock(OrderingRoundRepository.class);
    private final ServiceOrderRepository orders = mock(ServiceOrderRepository.class);
    private final ClinicalImagingRequestReplayRepository replays = mock(ClinicalImagingRequestReplayRepository.class);
    private final Map<UUID, OrderingRoundJpaEntity> roundRows = new LinkedHashMap<>();
    private final Map<UUID, ServiceOrderJpaEntity> orderRows = new LinkedHashMap<>();
    private final Map<String, ClinicalImagingRequestReplayJpaEntity> replayRows = new HashMap<>();
    private final UUID visitId = UUID.randomUUID(), patientId = UUID.randomUUID(), doctorId = UUID.randomUUID();
    private final UUID serviceId = UUID.randomUUID();
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-04T10:30:00.123456789Z"), ZoneOffset.UTC);
    private final ClinicalImagingRequestService service = serviceUsing(catalog);

    @BeforeEach void setUp() {
        when(visits.getVisitContext(visitId)).thenReturn(new VisitClinicalContextPort.ClinicalVisitContext(visitId, patientId, doctorId, true));
        when(catalog.getService(serviceId)).thenReturn(snapshot(ClinicalServiceType.IMAGING, true));
        when(rounds.findTopByVisitIdOrderByRoundNumberDesc(any())).thenAnswer(invocation -> roundRows.values().stream()
                .filter(round -> round.getVisitId().equals(invocation.getArgument(0)))
                .max(Comparator.comparingInt(OrderingRoundJpaEntity::getRoundNumber)));
        when(rounds.save(any())).thenAnswer(invocation -> {
            OrderingRoundJpaEntity value = invocation.getArgument(0); roundRows.put(value.getId(), value); return value;
        });
        when(orders.save(any())).thenAnswer(invocation -> {
            ServiceOrderJpaEntity value = invocation.getArgument(0); orderRows.put(value.getId(), value); return value;
        });
        when(rounds.findById(any())).thenAnswer(invocation -> Optional.ofNullable(roundRows.get(invocation.getArgument(0))));
        when(orders.findByOrderingRoundId(any())).thenAnswer(invocation -> orderRows.values().stream()
                .filter(order -> order.getOrderingRoundId().equals(invocation.getArgument(0))).toList());
        when(replays.findByDoctorProfileIdAndIdempotencyKey(any(), any())).thenAnswer(invocation ->
                Optional.ofNullable(replayRows.get(invocation.getArgument(0) + ":" + invocation.getArgument(1))));
        when(replays.saveAndFlush(any())).thenAnswer(invocation -> {
            ClinicalImagingRequestReplayJpaEntity value = invocation.getArgument(0);
            replayRows.put(value.getDoctorProfileId() + ":" + value.getIdempotencyKey(), value); return value;
        });
    }

    @Test void firstSubmissionCreatesOneRoundMultipleImagingOrdersAndDurableReplay() {
        var result = service.submit(new ImagingRequestCommand(visitId, doctorId, " key ", List.of(order(), order())));
        assertFalse(result.replayed());
        assertEquals(1, roundRows.size()); assertEquals(2, orderRows.size()); assertEquals(1, replayRows.size());
        assertEquals(1, result.round().roundNumber());
        assertEquals(Instant.parse("2026-11-04T10:30:00.123456Z"), result.round().orderedAt());
        assertEquals(result.round().id(), replayRows.get(doctorId + ":key").getOrderingRoundId());
        assertTrue(replayRows.get(doctorId + ":key").getRequestFingerprint().matches("[0-9a-f]{64}"));
        assertTrue(result.round().orders().stream().allMatch(value -> value.serviceType() == ClinicalServiceType.IMAGING));
    }

    @Test void exactRetryReturnsSameRoundIdsCodesAndSnapshotsWithoutCatalogOrWrites() {
        var first = service.submit(command("key"));
        clearInvocations(catalog, rounds, orders, replays, visits);
        when(catalog.getService(any())).thenThrow(new IllegalStateException("Catalog unavailable after initial commit"));
        var replay = service.submit(command("key"));
        assertTrue(replay.replayed()); assertEquals(first.round(), replay.round());
        verifyNoInteractions(catalog);
        verify(rounds, never()).save(any()); verify(orders, never()).save(any()); verify(replays, never()).saveAndFlush(any());
        verify(visits).getVisitContext(visitId);
        assertEquals(1, roundRows.size()); assertEquals(1, orderRows.size());
    }

    @ParameterizedTest @ValueSource(strings = {"visit", "service", "reason", "bodyArea", "instructions"})
    void keyReuseWithChangedCanonicalFieldConflicts(String change) {
        service.submit(command("key"));
        var value = order();
        var changed = new ImagingOrderRequest(change.equals("service") ? UUID.randomUUID() : serviceId,
                change.equals("reason") ? "Different reason" : value.clinicalReason(),
                change.equals("bodyArea") ? "LEFT_KNEE" : value.bodyArea(),
                change.equals("instructions") ? "Different instructions" : value.instructions());
        assertCode("IDEMPOTENCY_CONFLICT", () -> service.submit(new ImagingRequestCommand(
                change.equals("visit") ? UUID.randomUUID() : visitId, doctorId, "key", List.of(changed))));
        assertEquals(1, roundRows.size()); assertEquals(1, replayRows.size());
    }

    @Test void differentKeyCreatesAnotherRoundAndDoctorScopeIsIndependent() {
        var first = service.submit(command("key"));
        var second = service.submit(command("other-key"));
        assertNotEquals(first.round().id(), second.round().id()); assertEquals(2, second.round().roundNumber());
        UUID otherDoctor = UUID.randomUUID(), otherVisit = UUID.randomUUID();
        when(visits.getVisitContext(otherVisit)).thenReturn(new VisitClinicalContextPort.ClinicalVisitContext(otherVisit, patientId, otherDoctor, true));
        var third = service.submit(new ImagingRequestCommand(otherVisit, otherDoctor, "key", List.of(order())));
        assertFalse(third.replayed()); assertEquals(3, replayRows.size());
    }

    @ParameterizedTest @ValueSource(strings = {"LABORATORY", "OTHER"})
    void nonImagingCatalogTypesAreRejectedBeforeAnyWrite(String type) {
        when(catalog.getService(serviceId)).thenReturn(snapshot(ClinicalServiceType.valueOf(type), true));
        assertCode("MSG-39", () -> service.submit(command("key")));
        assertNoWrites();
    }

    @Test void examinationCatalogServiceIsRejectedByRealCatalogAdapterBeforeAnyWrite() {
        var publicCatalog = mock(fu.fa26.sep490.g58.otcms_be.servicecatalog.service.GetClinicServiceCatalogService.class);
        when(publicCatalog.getService(serviceId)).thenReturn(new fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicService(
                serviceId, "Examination", null, BigDecimal.TEN, Duration.ofMinutes(20), Duration.ZERO, true, true));
        var withRealAdapter = serviceUsing(new ServiceCatalogClinicalAdapter(publicCatalog));
        assertCode("MSG-39", () -> withRealAdapter.submit(command("key")));
        assertNoWrites();
    }

    @ParameterizedTest @ValueSource(strings = {"missing", "inactive", "negativePrice", "invalidPrecision"})
    void invalidCatalogSnapshotsCannotCommit(String invalid) {
        var snapshot = snapshot(ClinicalServiceType.IMAGING, !invalid.equals("inactive"));
        if (invalid.equals("missing")) snapshot = null;
        if (invalid.equals("negativePrice") || invalid.equals("invalidPrecision")) snapshot =
                new ClinicServiceCatalogPort.ClinicalServiceSnapshot(serviceId, "X-ray Knee", ClinicalServiceType.IMAGING,
                        new BigDecimal(invalid.equals("negativePrice") ? "-1" : "1.001"), true);
        when(catalog.getService(serviceId)).thenReturn(snapshot);
        assertCode("MSG-39", () -> service.submit(command("key"))); assertNoWrites();
    }

    @ParameterizedTest @ValueSource(strings = {"missing", "wrongDoctor", "notAllowed", "noPatient"})
    void visitAuthorizationFailsBeforeCatalogOrWrites(String problem) {
        when(visits.getVisitContext(visitId)).thenReturn(problem.equals("missing") ? null :
                new VisitClinicalContextPort.ClinicalVisitContext(visitId, problem.equals("noPatient") ? null : patientId,
                        problem.equals("wrongDoctor") ? UUID.randomUUID() : doctorId, !problem.equals("notAllowed")));
        String code = switch (problem) { case "missing" -> "MSG-41"; case "wrongDoctor" -> "MSG-40";
            case "notAllowed" -> "MSG-42"; default -> "MSG-39"; };
        assertCode(code, () -> service.submit(command("key")));
        verifyNoInteractions(catalog); assertNoWrites();
    }

    @Test void retryRechecksVisitAuthorizationAndDoesNotCreateMoreWork() {
        service.submit(command("key"));
        when(visits.getVisitContext(visitId)).thenReturn(new VisitClinicalContextPort.ClinicalVisitContext(visitId, patientId, doctorId, false));
        assertCode("MSG-42", () -> service.submit(command("key")));
        assertEquals(1, roundRows.size()); assertEquals(1, orderRows.size());
    }

    @Test void commandValidationRejectsMissingBlankAndOversizedKeysAndInvalidOrders() {
        for (String key : Arrays.asList(null, " ", "k".repeat(129))) {
            assertCode("MSG-39", () -> service.submit(command(key)));
        }
        for (List<ImagingOrderRequest> items : Arrays.<List<ImagingOrderRequest>>asList(null, List.of(), Arrays.asList((ImagingOrderRequest) null),
                List.of(new ImagingOrderRequest(null, "Reason", "Knee", null)),
                List.of(new ImagingOrderRequest(serviceId, " ", "Knee", null)),
                List.of(new ImagingOrderRequest(serviceId, "Reason", " ", null)))) {
            assertCode("MSG-39", () -> service.submit(new ImagingRequestCommand(visitId, doctorId, "key", items)));
        }
        verifyNoInteractions(visits, catalog); assertNoWrites();
    }

    @Test void fingerprintIsStableUnambiguousAndSensitiveToOrderAndNullInstructions() {
        var first = new ImagingOrderRequest(serviceId, "a\nb", "c", null);
        var second = new ImagingOrderRequest(serviceId, "a", "b\nc", "");
        var a = new ImagingRequestCommand(visitId, doctorId, "key", List.of(first, second));
        var b = new ImagingRequestCommand(visitId, doctorId, "other-key", List.of(first, second));
        String hash = ClinicalImagingRequestFingerprint.fingerprint(a);
        assertEquals(hash, ClinicalImagingRequestFingerprint.fingerprint(b));
        assertNotEquals(hash, ClinicalImagingRequestFingerprint.fingerprint(new ImagingRequestCommand(visitId, doctorId, "key", List.of(second, first))));
        assertNotEquals(ClinicalImagingRequestFingerprint.fingerprint(new ImagingRequestCommand(visitId, doctorId, "key", List.of(first))),
                ClinicalImagingRequestFingerprint.fingerprint(new ImagingRequestCommand(visitId, doctorId, "key",
                        List.of(new ImagingOrderRequest(serviceId, "a\nb", "c", "")))));
    }

    @Test void missingPersistedRoundIsNotTreatedAsSuccessfulReplay() {
        service.submit(command("key")); roundRows.clear();
        assertCode("DEPENDENCY_UNAVAILABLE", () -> service.submit(command("key")));
    }

    private ClinicalImagingRequestService serviceUsing(ClinicServiceCatalogPort catalogPort) {
        var preparation = new ClinicalOrderPreparationService(visits, catalogPort);
        var writer = new ClinicalOrderingTransactionService(rounds, orders, clock);
        return new ClinicalImagingRequestServiceImpl(new ClinicalImagingRequestTransactionService(preparation, writer, replays, rounds, orders, clock));
    }

    private ImagingRequestCommand command(String key) { return new ImagingRequestCommand(visitId, doctorId, key, List.of(order())); }
    private ImagingOrderRequest order() { return new ImagingOrderRequest(serviceId, "Suspected injury", "RIGHT_KNEE", "Weight-bearing"); }
    private ClinicServiceCatalogPort.ClinicalServiceSnapshot snapshot(ClinicalServiceType type, boolean active) {
        return new ClinicServiceCatalogPort.ClinicalServiceSnapshot(serviceId, "X-ray Knee", type, new BigDecimal("250000.00"), active);
    }
    private void assertNoWrites() {
        verify(rounds, never()).save(any()); verify(orders, never()).save(any()); verify(replays, never()).saveAndFlush(any());
    }
    private static void assertCode(String code, org.junit.jupiter.api.function.Executable action) {
        assertEquals(code, assertThrows(ClinicalOperationException.class, action).code());
    }
}
