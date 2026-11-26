package fu.fa26.sep490.g58.otcms_be.clinical.service.impl;

import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.ServiceOrderRepository;
import fu.fa26.sep490.g58.otcms_be.clinical.service.LaboratoryCollectionService;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditEntry;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditLogWriter;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.LaboratorySampleService;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.LaboratoryResultValue;
import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicServiceType;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.LabIndicator;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.GetClinicServiceCatalogService;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffRoomAssignmentQueryService;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LaboratoryCollectionServiceImpl implements LaboratoryCollectionService {
    private final ServiceOrderRepository orders;
    private final StaffRoomAssignmentQueryService assignments;
    private final LaboratorySampleService samples;
    private final CoordinationGuardService guard;
    private final AuditLogWriter audit;
    private final Clock clock;
    private final fu.fa26.sep490.g58.otcms_be.diagnostic.service.LaboratoryDraftService drafts;
    private final fu.fa26.sep490.g58.otcms_be.staff.service.DoctorProfileIdentityQueryService doctors;
    private final fu.fa26.sep490.g58.otcms_be.notification.service.NotificationPublisher notifications;
    private final GetClinicServiceCatalogService serviceCatalog;
    private final PatientProfileDirectory patients;
    public LaboratoryCollectionServiceImpl(ServiceOrderRepository orders, StaffRoomAssignmentQueryService assignments,
            LaboratorySampleService samples, CoordinationGuardService guard, AuditLogWriter audit, Clock clock,
            fu.fa26.sep490.g58.otcms_be.diagnostic.service.LaboratoryDraftService drafts,
            fu.fa26.sep490.g58.otcms_be.staff.service.DoctorProfileIdentityQueryService doctors,
            fu.fa26.sep490.g58.otcms_be.notification.service.NotificationPublisher notifications,
            GetClinicServiceCatalogService serviceCatalog, PatientProfileDirectory patients) {
        this.orders = orders; this.assignments = assignments; this.samples = samples; this.guard = guard; this.audit = audit; this.clock = clock; this.drafts = drafts; this.doctors = doctors; this.notifications = notifications; this.serviceCatalog = serviceCatalog; this.patients = patients;
    }
    @Override @Transactional
    public void approve(UUID orderId, AuthenticatedAccount actor, boolean checked, boolean critical) {
        if (!checked) throw new ClinicalOperationException("MSG-39");
        guard.acquire();
        var sample = latest(orderId, actor);
        var now = clock.instant();
        if (sample == null) throw new ClinicalOperationException("MSG-42");
        var order = orders.findById(orderId).orElseThrow(() -> new ClinicalOperationException("MSG-41"));
        var doctorAccount = doctors.findDoctorAccountId(order.getRequestingDoctorId()).orElseThrow(() -> new ClinicalOperationException("MSG-41"));
        try { drafts.approve(orderId, actor.accountId(), now); } catch (IllegalStateException invalid) { throw new ClinicalOperationException("MSG-42"); }
        if (orders.releaseLabResult(orderId, now) != 1) throw new ClinicalOperationException("MSG-42");
        var state = new java.util.LinkedHashMap<String, Object>();
        state.put("sampleId", sample.id().toString()); state.put("critical", critical);
        audit.record(AuditEntry.builder("laboratory", "LABORATORY", "RELEASE_RESULT", "SERVICE_ORDER").entityId(orderId)
                .actorAccountId(actor.accountId()).actorType("LAB_TECHNICIAN").afterState(state).occurredAt(now).build());
        notifications.publish(doctorAccount, new fu.fa26.sep490.g58.otcms_be.notification.model.NotificationMessage("LAB_RESULT",
                critical ? "Kết quả xét nghiệm nguy cấp cần được xử lý" : "Kết quả xét nghiệm đã được duyệt",
                critical ? "Có kết quả xét nghiệm nguy cấp mới. Vui lòng xem kết quả và thông báo cho bệnh nhân."
                        : "Có kết quả xét nghiệm mới trong lượt khám của bạn.",
                fu.fa26.sep490.g58.otcms_be.notification.model.NotificationRelatedKind.VISIT, order.getVisitId()));
    }
    @Override @Transactional
    public LaboratorySampleService.Sample collect(UUID orderId, boolean identityConfirmed, AuthenticatedAccount actor) {
        if (!identityConfirmed) throw new ClinicalOperationException("MSG-39");
        return perform(orderId, actor, "ASSIGNED", "COLLECTED");
    }
    @Override @Transactional(readOnly = true)
    public LaboratorySampleService.Sample latest(UUID orderId, AuthenticatedAccount actor) {
        if (actor == null || actor.accountId() == null || actor.accountRole() != AccountRole.LAB_TECHNICIAN) throw new ClinicalOperationException("MSG-40");
        var assignment = orders.findLaboratoryAssignment(orderId).orElseThrow(() -> new ClinicalOperationException("MSG-41"));
        if (assignment.getRoomId() == null || !assignments.isLabTechnicianAssigned(actor.accountId(), assignment.getRoomId(), clock.instant())) throw new ClinicalOperationException("MSG-40");
        return samples.latest(orderId);
    }
    @Override @Transactional
    public java.util.List<fu.fa26.sep490.g58.otcms_be.diagnostic.model.LaboratoryResultValue> readDraft(UUID orderId, AuthenticatedAccount actor) {
        var sample = latest(orderId, actor);
        if (sample == null) throw new ClinicalOperationException("MSG-42");
        var values = drafts.read(orderId);
        if (!values.isEmpty()) return values;
        var order = orders.findById(orderId).orElseThrow(() -> new ClinicalOperationException("MSG-41"));
        return drafts.initialize(orderId, order.getVisitId(), sample.id(), actor.accountId(), configuredValues(order), clock.instant());
    }
    @Override @Transactional(readOnly = true)
    public java.util.List<fu.fa26.sep490.g58.otcms_be.diagnostic.model.LaboratoryResultValue> readResult(UUID orderId, AuthenticatedAccount actor) {
        latest(orderId, actor);
        var assignment = orders.findLaboratoryAssignment(orderId).orElseThrow(() -> new ClinicalOperationException("MSG-41"));
        if (!"RESULT_AVAILABLE".equals(assignment.getLabStatus())) throw new ClinicalOperationException("MSG-42");
        return drafts.issued(orderId);
    }
    @Override @Transactional
    public java.util.List<fu.fa26.sep490.g58.otcms_be.diagnostic.model.LaboratoryResultValue> saveDraft(UUID orderId, AuthenticatedAccount actor,
            java.util.List<fu.fa26.sep490.g58.otcms_be.diagnostic.model.LaboratoryResultValue> values) {
        guard.acquire();
        var sample = latest(orderId, actor);
        var assignment = orders.findLaboratoryAssignment(orderId).orElseThrow(() -> new ClinicalOperationException("MSG-41"));
        if (!"COLLECTED".equals(assignment.getLabStatus()) || sample == null) throw new ClinicalOperationException("MSG-42");
        var order = orders.findById(orderId).orElseThrow(() -> new ClinicalOperationException("MSG-41"));
        var configuredValues = drafts.read(orderId);
        if (configuredValues.isEmpty() || values == null || values.size() != configuredValues.size())
            throw new ClinicalOperationException("MSG-39");
        var submittedValues = new java.util.ArrayList<LaboratoryResultValue>(configuredValues.size());
        for (int index = 0; index < configuredValues.size(); index++) {
            var configured = configuredValues.get(index);
            var submitted = values.get(index);
            if (submitted == null) throw new ClinicalOperationException("MSG-39");
            submittedValues.add(new LaboratoryResultValue(configured.indicator(), submitted.value(),
                    configured.unit(), configured.referenceRange(), submitted.comment(), submitted.abnormal()));
        }
        if (!fu.fa26.sep490.g58.otcms_be.diagnostic.validation.LaboratoryResultValidation.validDraft(submittedValues))
            throw new ClinicalOperationException("MSG-39");
        var result = drafts.save(orderId, order.getVisitId(), sample.id(), actor.accountId(), submittedValues, clock.instant());
        audit.record(AuditEntry.builder("laboratory", "LABORATORY", "SAVE_RESULT_DRAFT", "SERVICE_ORDER").entityId(orderId)
                .actorAccountId(actor.accountId()).actorType("LAB_TECHNICIAN").afterState(Map.of("sampleId", sample.id().toString())).occurredAt(clock.instant()).build());
        return result;
    }
    private LaboratorySampleService.Sample perform(UUID orderId, AuthenticatedAccount actor, String expected, String next) {
        if (actor == null || actor.accountId() == null || actor.accountRole() != AccountRole.LAB_TECHNICIAN) throw new ClinicalOperationException("MSG-40");
        guard.acquire();
        var now = clock.instant();
        var assignment = orders.findLaboratoryAssignment(orderId).orElseThrow(() -> new ClinicalOperationException("MSG-41"));
        if (assignment.getRoomId() == null || !assignments.isLabTechnicianAssigned(actor.accountId(), assignment.getRoomId(), now)) throw new ClinicalOperationException("MSG-40");
        if (!expected.equals(assignment.getLabStatus()) || orders.transitionLabStatus(orderId, expected, next, now) != 1) throw new ClinicalOperationException("MSG-42");
        var sample = samples.collect(orderId, actor.accountId(), now);
        if ("COLLECTED".equals(next)) {
            var order = orders.findById(orderId).orElseThrow(() -> new ClinicalOperationException("MSG-41"));
            drafts.initialize(orderId, order.getVisitId(), sample.id(), actor.accountId(), configuredValues(order), now);
        }
        audit.record(AuditEntry.builder("laboratory", "LABORATORY", next, "SERVICE_ORDER").entityId(orderId)
                .actorAccountId(actor.accountId()).actorType("LAB_TECHNICIAN").afterState(Map.of("sampleId", sample.id().toString())).occurredAt(now).build());
        return sample;
    }

    private java.util.List<LaboratoryResultValue> configuredValues(
            fu.fa26.sep490.g58.otcms_be.clinical.model.entity.ServiceOrderJpaEntity order) {
        var service = serviceCatalog.getService(order.getServiceId());
        if (service.type() != ClinicServiceType.LABORATORY || service.indicators().isEmpty())
            throw new ClinicalOperationException("MSG-42");
        var profile = patients.findProfile(order.getPatientId()).orElseThrow(() -> new ClinicalOperationException("MSG-41"));
        boolean female = "FEMALE".equalsIgnoreCase(profile.gender());
        return service.indicators().stream().map(indicator -> toConfiguredValue(indicator, female)).toList();
    }

    private LaboratoryResultValue toConfiguredValue(LabIndicator indicator, boolean female) {
        var range = female ? indicator.refFemale() : indicator.refMale();
        String referenceRange = range == null ? "" : number(range.min()) + " - " + number(range.max());
        return new LaboratoryResultValue(indicator.name(), "", indicator.unit(), referenceRange, "", null);
    }

    private String number(double value) {
        return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}
