package fu.fa26.sep490.g58.otcms_be.clinical;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import fu.fa26.sep490.g58.otcms_be.auth.repository.AccountRepository;
import fu.fa26.sep490.g58.otcms_be.auth.service.impl.DoctorAccountAccessServiceImpl;
import fu.fa26.sep490.g58.otcms_be.clinical.controller.*;
import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.integration.servicecatalog.ServiceCatalogClinicalAdapter;
import fu.fa26.sep490.g58.otcms_be.clinical.integration.visit.VisitClinicalContextAdapter;
import fu.fa26.sep490.g58.otcms_be.clinical.model.entity.*;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.*;
import fu.fa26.sep490.g58.otcms_be.clinical.security.ClinicalDoctorIdentityResolver;
import fu.fa26.sep490.g58.otcms_be.clinical.service.*;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalImagingRequestService.*;
import fu.fa26.sep490.g58.otcms_be.clinical.service.impl.*;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.diagnostic.controller.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.clinical.ClinicalImagingOrderAdapter;
import fu.fa26.sep490.g58.otcms_be.diagnostic.mapper.DiagnosticDtoMapper;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticActor;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticRequestJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.impl.*;
import fu.fa26.sep490.g58.otcms_be.patient.integration.visit.VisitDoctorCareRelationAdapter;
import fu.fa26.sep490.g58.otcms_be.patient.service.DoctorCareRelationPort;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.integration.diagnostic.ImagingServiceCatalogAdapter;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicServiceType;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.entity.ClinicServiceJpaEntity;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.repository.ClinicServiceRepository;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.impl.GetClinicServiceCatalogServiceImpl;
import fu.fa26.sep490.g58.otcms_be.staff.model.entity.DoctorProfileJpaEntity;
import fu.fa26.sep490.g58.otcms_be.staff.repository.DoctorProfileRepository;
import fu.fa26.sep490.g58.otcms_be.staff.service.impl.DoctorProfileIdentityQueryServiceImpl;
import fu.fa26.sep490.g58.otcms_be.visit.integration.diagnostic.VisitDiagnosticReadAdapter;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.*;
import fu.fa26.sep490.g58.otcms_be.visit.repository.*;
import fu.fa26.sep490.g58.otcms_be.visit.service.impl.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.*;
import org.springframework.data.domain.PageImpl;
import org.springframework.mock.web.MockServletContext;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.*;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** Production controllers, services and adapters; only persistence is doubled. Does not claim PostgreSQL execution. */
class DoctorCoreProductionWiringTest {
    private AnnotationConfigWebApplicationContext context;
    private final UUID visitId = UUID.randomUUID(), patientId = UUID.randomUUID(), doctorId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID(), serviceId = UUID.randomUUID();
    private final Map<UUID, OrderingRoundJpaEntity> roundRows = new LinkedHashMap<>();
    private final Map<UUID, ServiceOrderJpaEntity> orderRows = new LinkedHashMap<>();
    private final Map<String, ClinicalImagingRequestReplayJpaEntity> replayRows = new HashMap<>();
    private final Map<UUID, DiagnosticRequestJpaEntity> diagnosticRows = new LinkedHashMap<>();

    @BeforeEach void startRealApplicationWiring() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext()); context.register(ProductionConfiguration.class); context.refresh();
        var profile = mock(DoctorProfileJpaEntity.class);
        when(profile.id()).thenReturn(doctorId); when(profile.accountId()).thenReturn(accountId);
        when(bean(DoctorProfileRepository.class).findByAccountId(accountId)).thenReturn(Optional.of(profile));
        when(bean(DoctorProfileRepository.class).findById(doctorId)).thenReturn(Optional.of(profile));
        when(bean(AccountRepository.class).existsByIdAndAccountTypeAndStatus(eq(accountId), any(), any())).thenReturn(true);
        when(bean(ClinicVisitJpaRepository.class).findById(visitId)).thenReturn(Optional.of(
                ClinicVisitJpaEntity.walkIn(visitId, patientId, null, UUID.randomUUID(), Instant.EPOCH)));
        when(bean(VisitCareAssignmentRepository.class).findFirstByVisitIdAndStatusOrderByAssignedAtDescIdDesc(visitId, "IN_PROGRESS"))
                .thenReturn(Optional.of(VisitCareAssignmentJpaEntity.inProgress(UUID.randomUUID(), visitId, doctorId,
                        "ORTHOPEDIC", 30, Instant.EPOCH, Instant.EPOCH.plusSeconds(1800))));
        var catalogService = new ClinicServiceJpaEntity(serviceId, "Knee MRI", "", ClinicServiceType.IMAGING,
                new BigDecimal("250000"), 30, 0, false, true);
        catalogService.updateCatalogDetails("MRI", null, List.of());
        when(bean(ClinicServiceRepository.class).findById(serviceId)).thenReturn(Optional.of(catalogService));
        var rounds = bean(OrderingRoundRepository.class);
        when(rounds.findTopByVisitIdOrderByRoundNumberDesc(any())).thenAnswer(call -> roundRows.values().stream()
                .filter(row -> row.getVisitId().equals(call.getArgument(0))).max(Comparator.comparingInt(OrderingRoundJpaEntity::getRoundNumber)));
        when(rounds.save(any())).thenAnswer(call -> { assertInTransaction(); var row = (OrderingRoundJpaEntity) call.getArgument(0); roundRows.put(row.getId(), row); return row; });
        when(rounds.findById(any())).thenAnswer(call -> Optional.ofNullable(roundRows.get(call.getArgument(0))));
        var orders = bean(ServiceOrderRepository.class);
        when(orders.save(any())).thenAnswer(call -> { assertInTransaction(); var row = (ServiceOrderJpaEntity) call.getArgument(0); orderRows.put(row.getId(), row); return row; });
        when(orders.findById(any())).thenAnswer(call -> Optional.ofNullable(orderRows.get(call.getArgument(0))));
        when(orders.findByOrderingRoundId(any())).thenAnswer(call -> orderRows.values().stream().filter(row -> row.getOrderingRoundId().equals(call.getArgument(0))).toList());
        var replays = bean(ClinicalImagingRequestReplayRepository.class);
        when(replays.findByDoctorProfileIdAndIdempotencyKey(any(), any())).thenAnswer(call -> Optional.ofNullable(replayRows.get(call.getArgument(0) + ":" + call.getArgument(1))));
        when(replays.saveAndFlush(any())).thenAnswer(call -> { assertInTransaction(); var row = (ClinicalImagingRequestReplayJpaEntity) call.getArgument(0); replayRows.put(row.getDoctorProfileId() + ":" + row.getIdempotencyKey(), row); return row; });
        var requests = bean(DiagnosticRequestJpaRepository.class);
        when(requests.findFirstByServiceOrderIdOrderByCreatedAtDescIdDesc(any())).thenAnswer(call -> Optional.ofNullable(diagnosticRows.get(call.getArgument(0))));
        when(requests.insertUnassigned(any(), any(), any(), any(), any(), any(), any(), any(), any())).thenAnswer(call -> {
            assertInTransaction(); UUID order = call.getArgument(8);
            if (diagnosticRows.containsKey(order)) return 0;
            var row = new DiagnosticRequestJpaEntity(call.getArgument(0), call.getArgument(1), call.getArgument(2),
                    call.getArgument(3), call.getArgument(4), null, call.getArgument(5), call.getArgument(6), call.getArgument(7),
                    DiagnosticRequestStatus.PENDING, null, null, null);
            row.setServiceOrderId(order); row.setCreatedAt(Instant.EPOCH); diagnosticRows.put(order, row); return 1;
        });
        when(requests.findById(any())).thenAnswer(call -> diagnosticRows.values().stream().filter(row -> row.getId().equals(call.getArgument(0))).findFirst());
        when(requests.findAllByVisitIdOrderByCreatedAtDescIdDesc(eq(visitId), any())).thenAnswer(call -> new PageImpl<>(new ArrayList<>(diagnosticRows.values())));
    }

    @AfterEach void close() { context.close(); }
    private <T> T bean(Class<T> type) { return context.getBean(type); }
    private static void assertInTransaction() { assertTrue(TransactionSynchronizationManager.isActualTransactionActive()); }
    private ImagingRequestCommand command(String key, int count) {
        return new ImagingRequestCommand(visitId, doctorId, key, Collections.nCopies(count,
                new ImagingOrderRequest(serviceId, "Meniscal injury", "RIGHT_KNEE", "AP/lateral")));
    }

    @Test void allProductionControllersAndRequiredAdaptersWireWithExactlyOneUc38Post() {
        var handlers = bean(RequestMappingHandlerMapping.class).getHandlerMethods().entrySet().stream().filter(entry ->
                entry.getKey().getPatternValues().contains("/api/v1/visits/{visitId}/imaging-requests")
                        && entry.getKey().getMethodsCondition().getMethods().contains(RequestMethod.POST)).toList();
        assertEquals(1, handlers.size()); assertEquals(ClinicalImagingRequestController.class, handlers.getFirst().getValue().getBeanType());
        assertInstanceOf(VisitDiagnosticReadAdapter.class, bean(VisitDiagnosticReadPort.class));
        assertInstanceOf(ImagingServiceCatalogAdapter.class, bean(ImagingServiceCatalogPort.class));
        assertInstanceOf(ClinicalImagingOrderAdapter.class, bean(ClinicalImagingOrderPort.class));
        assertInstanceOf(VisitDoctorCareRelationAdapter.class, bean(DoctorCareRelationPort.class));
        assertNotNull(bean(DiagnosticImagingRequestRegistration.class));
        assertTrue(context.getBeansOfType(VisitDiagnosticPort.class).isEmpty(), "Unimplemented result coordination must not be hidden by a no-op");
    }

    @Test void multiOrderSubmissionAndReplayUseSameTransactionAndNoDuplicateOperationalWork() {
        var service = bean(ClinicalImagingRequestService.class);
        var created = service.submit(command("same-key", 2));
        assertEquals(1, roundRows.size()); assertEquals(2, diagnosticRows.size()); assertEquals(1, replayRows.size());
        assertEquals(1, bean(RecordingTransactions.class).begins);
        assertTrue(diagnosticRows.values().stream().allMatch(row -> accountId.equals(row.getOrderingDoctorId()) && row.getRoomId() == null));
        clearInvocations(bean(ClinicServiceRepository.class), bean(DiagnosticRequestJpaRepository.class));
        var replay = service.submit(command("same-key", 2));
        assertTrue(replay.replayed()); assertEquals(created.round(), replay.round()); assertEquals(2, diagnosticRows.size());
        verify(bean(DiagnosticRequestJpaRepository.class), never()).insertUnassigned(any(), any(), any(), any(), any(), any(), any(), any(), any());
        verifyNoInteractions(bean(ClinicServiceRepository.class));
        service.submit(command("different-key", 1)); assertEquals(2, roundRows.size()); assertEquals(3, diagnosticRows.size());
    }

    @Test void registrationFailureRollsBackWholeClinicalAttemptBeforeReplayIsSaved() {
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("Unavailable"))
                .when(bean(DiagnosticRequestJpaRepository.class)).insertUnassigned(any(), any(), any(), any(), any(), any(), any(), any(), any());
        assertThrows(org.springframework.dao.DataAccessResourceFailureException.class, () -> bean(ClinicalImagingRequestService.class).submit(command("failed", 1)));
        assertEquals(1, bean(RecordingTransactions.class).rollbacks); assertEquals(0, bean(RecordingTransactions.class).commits);
        verify(bean(ClinicalImagingRequestReplayRepository.class), never()).saveAndFlush(any());
        // Repository doubles do not prove PostgreSQL rollback; the real Spring transaction boundary is verified here.
    }

    @Test void wrongDoctorIsRejectedBeforeClinicalOrDiagnosticWrites() {
        var bad = new ImagingRequestCommand(visitId, UUID.randomUUID(), "bad", command("", 1).orders());
        assertEquals("MSG-40", assertThrows(ClinicalOperationException.class, () -> bean(ClinicalImagingRequestService.class).submit(bad)).code());
        verify(bean(OrderingRoundRepository.class), never()).save(any()); assertTrue(diagnosticRows.isEmpty());
    }

    @Test void statusUsesRealAccountToProfileAndVisitEvidenceForListAndDetail() {
        bean(ClinicalImagingRequestService.class).submit(command("status", 1));
        var service = bean(DiagnosticStatusService.class);
        var actor = DiagnosticActor.user(accountId, AccountRole.DOCTOR);
        assertEquals(1, service.listVisitStatus(actor, visitId, 0, 20).items().size());
        assertNull(service.getStatus(actor, diagnosticRows.values().iterator().next().getId()).roomId());
        assertThrows(fu.fa26.sep490.g58.otcms_be.diagnostic.exception.DiagnosticOperationException.class,
                () -> service.listVisitStatus(DiagnosticActor.user(UUID.randomUUID(), AccountRole.DOCTOR), visitId, 0, 20));
        assertThrows(fu.fa26.sep490.g58.otcms_be.diagnostic.exception.DiagnosticOperationException.class,
                () -> service.getStatus(DiagnosticActor.user(UUID.randomUUID(), AccountRole.DOCTOR), diagnosticRows.values().iterator().next().getId()));
    }

    @Test void registrationOutsideTransactionIsRejected() {
        assertThrows(IllegalTransactionStateException.class, () -> bean(DiagnosticImagingRequestRegistration.class).register(null));
    }

    @Test void replayRepairsPreIntegrationRoundWithoutCreatingAnotherClinicalRound() {
        var first = bean(ClinicalImagingRequestService.class).submit(command("legacy-round", 1));
        diagnosticRows.clear();
        var replay = bean(ClinicalImagingRequestService.class).submit(command("legacy-round", 1));
        assertTrue(replay.replayed()); assertEquals(first.round(), replay.round());
        assertEquals(1, roundRows.size()); assertEquals(1, orderRows.size()); assertEquals(1, diagnosticRows.size());
    }

    @Test void existingClinicalBodyAreaAndUnboundedReasonContractSurvivesRegistration() {
        var command = new ImagingRequestCommand(visitId, doctorId, "long-content", List.of(new ImagingOrderRequest(
                serviceId, "r".repeat(3000), "b".repeat(255), null)));
        bean(ClinicalImagingRequestService.class).submit(command);
        var request = diagnosticRows.values().iterator().next();
        assertEquals(3000, request.getClinicalReason().length()); assertEquals(255, request.getBodyArea().length());
    }

    @Test void medicalDocumentProductionBeansAndUniqueUploadMappingAreRegistered() {
        var handlers = bean(RequestMappingHandlerMapping.class).getHandlerMethods().entrySet().stream().filter(entry ->
                entry.getKey().getPatternValues().contains("/api/v1/visits/{visitId}/medical-documents")
                        && entry.getKey().getMethodsCondition().getMethods().contains(RequestMethod.POST)).toList();
        assertEquals(1, handlers.size()); assertEquals(ClinicalMedicalDocumentController.class, handlers.getFirst().getValue().getBeanType());
        assertNotNull(bean(ClinicalMedicalDocumentService.class));
        assertNotNull(bean(fu.fa26.sep490.g58.otcms_be.clinical.security.ClinicalMedicalDocumentAccess.class));
        assertEquals(fu.fa26.sep490.g58.otcms_be.patient.service.impl.PatientProfileAccessServiceImpl.class,
                org.springframework.aop.support.AopUtils.getTargetClass(bean(fu.fa26.sep490.g58.otcms_be.patient.service.PatientProfileAccessService.class)));
    }

    @Configuration @EnableWebMvc @EnableTransactionManagement(proxyTargetClass = true)
    @Import({ClinicalImagingRequestController.class, ClinicalExaminationController.class, AfterVisitAdviceController.class,
            DiagnosticRequestController.class, DiagnosticFindingsController.class, DiagnosticHistoryController.class, DiagnosticDtoMapper.class,
            ClinicalMedicalDocumentController.class, ClinicalMedicalDocumentServiceImpl.class,
            fu.fa26.sep490.g58.otcms_be.clinical.security.ClinicalMedicalDocumentAccess.class,
            fu.fa26.sep490.g58.otcms_be.clinical.validation.ClinicalMedicalDocumentFileValidator.class,
            fu.fa26.sep490.g58.otcms_be.auth.service.impl.ReceptionistAccountAccessServiceImpl.class,
            fu.fa26.sep490.g58.otcms_be.auth.service.impl.PatientIdentityDirectoryServiceImpl.class,
            fu.fa26.sep490.g58.otcms_be.auth.service.impl.PatientProfileDirectoryServiceImpl.class,
            fu.fa26.sep490.g58.otcms_be.patient.service.impl.PatientProfileAccessServiceImpl.class,
            ClinicalImagingRequestServiceImpl.class, ClinicalImagingRequestTransactionService.class, ClinicalOrderingTransactionService.class,
            ClinicalOrderPreparationService.class, ClinicalOrderingServiceImpl.class, ClinicalExaminationServiceImpl.class, AfterVisitAdviceServiceImpl.class,
            ClinicalDoctorIdentityResolver.class, VisitClinicalContextAdapter.class, VisitClinicalCareContextServiceImpl.class,
            VisitDoctorCareRelationQueryServiceImpl.class, VisitDoctorCareRelationAdapter.class, VisitDiagnosticReadAdapter.class,
            DoctorProfileIdentityQueryServiceImpl.class, DoctorAccountAccessServiceImpl.class, ServiceCatalogClinicalAdapter.class,
            GetClinicServiceCatalogServiceImpl.class, ImagingServiceCatalogAdapter.class, ClinicalImagingOrderAdapter.class,
            DiagnosticImagingRequestRegistrationImpl.class, DiagnosticStatusServiceImpl.class, DiagnosticRequestServiceImpl.class,
            DiagnosticAuthorizationPolicyImpl.class, DiagnosticTransitionPolicyImpl.class, DiagnosticFindingsServiceImpl.class, DiagnosticHistoryServiceImpl.class})
    static class ProductionConfiguration {
        @Bean Clock clock() { return Clock.systemUTC(); }
        @Bean RecordingTransactions transactionManager() { return new RecordingTransactions(); }
        @Bean static BeanFactoryPostProcessor persistenceOnlyDoubles() {
            return factory -> {
                Class<?>[] repositories = {AccountRepository.class, DoctorProfileRepository.class, ClinicServiceRepository.class,
                        ClinicVisitJpaRepository.class, VisitCareAssignmentRepository.class, ClinicalExaminationRepository.class,
                        ClinicalDiagnosisRepository.class, OrderingRoundRepository.class, ServiceOrderRepository.class,
                        ClinicalImagingRequestReplayRepository.class, ClinicalMedicalDocumentRepository.class, ClinicalMedicalDocumentPayloadRepository.class,
                        fu.fa26.sep490.g58.otcms_be.auth.repository.PatientProfileRepository.class,
                        fu.fa26.sep490.g58.otcms_be.patient.repository.CareRelationshipRepository.class,
                        DiagnosticRequestJpaRepository.class, DiagnosticAttemptJpaRepository.class,
                        DiagnosticIdempotencyRecordJpaRepository.class, DiagnosticAuditEventJpaRepository.class,
                        DiagnosticFindingsVersionJpaRepository.class, DiagnosticReportJpaRepository.class,
                        DiagnosticOutboxJpaRepository.class, DiagnosticHistoryAccessAuditJpaRepository.class};
                for (Class<?> repository : repositories) factory.registerSingleton(repository.getSimpleName(), mock(repository));
            };
        }
    }

    static class RecordingTransactions extends AbstractPlatformTransactionManager {
        private final ThreadLocal<State> current = new ThreadLocal<>();
        int begins, commits, rollbacks;
        @Override protected Object doGetTransaction() { var state = current.get(); return state == null ? new State() : state; }
        @Override protected boolean isExistingTransaction(Object transaction) { return ((State) transaction).active; }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { ((State) transaction).active = true; current.set((State) transaction); begins++; }
        @Override protected void doCommit(DefaultTransactionStatus status) { commits++; }
        @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
        @Override protected void doSetRollbackOnly(DefaultTransactionStatus status) { ((State) status.getTransaction()).rollbackOnly = true; }
        @Override protected void doCleanupAfterCompletion(Object transaction) { ((State) transaction).active = false; current.remove(); }
        static class State implements SmartTransactionObject {
            boolean active, rollbackOnly;
            @Override public boolean isRollbackOnly() { return rollbackOnly; }
        }
    }
}
