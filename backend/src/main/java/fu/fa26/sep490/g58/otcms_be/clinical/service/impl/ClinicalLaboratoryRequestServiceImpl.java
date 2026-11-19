package fu.fa26.sep490.g58.otcms_be.clinical.service.impl;

import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalServiceType;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.ClinicalOrderingRoundRepository;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.ServiceOrderRepository;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalLaboratoryRequestService;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalOrderingService;
import fu.fa26.sep490.g58.otcms_be.clinical.service.VisitConclusionPort;
import fu.fa26.sep490.g58.otcms_be.common.idempotency.IdempotencyReplay;
import fu.fa26.sep490.g58.otcms_be.common.idempotency.IdempotencyScope;
import fu.fa26.sep490.g58.otcms_be.common.idempotency.IdempotencyStore;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClinicalLaboratoryRequestServiceImpl implements ClinicalLaboratoryRequestService {
    private final ClinicalOrderPreparationService preparation;
    private final ClinicalOrderingTransactionService writer;
    private final ServiceOrderRepository orders;
    private final ClinicalOrderingRoundRepository rounds;
    private final CoordinationGuardService guard;
    private final VisitConclusionPort conclusions;
    private final Clock clock;
    private final IdempotencyStore store;

    public ClinicalLaboratoryRequestServiceImpl(ClinicalOrderPreparationService preparation,
            ClinicalOrderingTransactionService writer, ServiceOrderRepository orders, ClinicalOrderingRoundRepository rounds,
            CoordinationGuardService guard, VisitConclusionPort conclusions, Clock clock, IdempotencyStore store) {
        this.preparation = preparation;
        this.writer = writer;
        this.orders = orders;
        this.rounds = rounds;
        this.guard = guard;
        this.conclusions = conclusions;
        this.clock = clock;
        this.store = store;
    }

    @Override
    @Transactional
    public SubmissionResult submit(SubmitRequest command) {
        validate(command);
        String key = command.idempotencyKey().trim();
        String fingerprint = fingerprint(command);
        guard.acquire();
        var existing = store.find(IdempotencyScope.ORDER_ROUND_SUBMIT, command.doctorProfileId(), key);
        if (existing.isPresent()) return replay(command, fingerprint, existing.get());
        var context = preparation.requireCurrentCare(command.visitId(), command.doctorProfileId());
        var requests = command.orders().stream().map(item -> new ClinicalOrderingService.OrderRequest(
                item.serviceId(), item.clinicalReason(), null, item.instructions())).toList();
        var snapshots = preparation.prepare(requests, false);
        for (var item : snapshots) {
            if (item.service().type() != ClinicalServiceType.LABORATORY || item.service().name().length() > 255) {
                throw new ClinicalOperationException("MSG-39");
            }
            try {
                if (item.service().currentPrice().setScale(2, RoundingMode.UNNECESSARY).precision() > 12) {
                    throw new ClinicalOperationException("MSG-39");
                }
            } catch (ArithmeticException invalidPrice) {
                throw new ClinicalOperationException("MSG-39");
            }
        }
        var round = writer.createRound(command.visitId(), context.patientId(), command.doctorProfileId(),
                snapshots, key, fingerprint);
        conclusions.markAwaitingConclusion(command.visitId(), clock.instant());
        return new SubmissionResult(sorted(round), false);
    }

    private SubmissionResult replay(SubmitRequest command, String fingerprint, IdempotencyReplay recorded) {
        if (!recorded.matches(fingerprint)) throw new ClinicalOperationException("IDEMPOTENCY_CONFLICT");
        var round = rounds.findById(recorded.resourceId())
                .orElseThrow(() -> new ClinicalOperationException("DEPENDENCY_UNAVAILABLE"));
        preparation.requireCurrentCare(command.visitId(), command.doctorProfileId());
        if (!command.visitId().equals(round.getVisitId())
                || !command.doctorProfileId().equals(round.getOrderedByDoctorId())) {
            throw new ClinicalOperationException("DEPENDENCY_UNAVAILABLE");
        }
        var items = orders.findLaboratoryRoundOrders(round.getId()).stream().map(order ->
                new ClinicalOrderingService.OrderView(order.getId(), order.getOrderCode(), order.getVisitId(),
                        order.getPatientId(), order.getOrderingRoundId(), order.getServiceId(), order.getServiceNameSnapshot(),
                        order.getServiceType(), order.getRequestingDoctorId(), order.getClinicalReason(), order.getBodyArea(),
                        order.getInstructions(), order.getPriceSnapshot(), order.getOrderStatus(), order.getPaymentStatus())).toList();
        return new SubmissionResult(sorted(new ClinicalOrderingService.RoundView(round.getId(), round.getVisitId(),
                round.getRoundNumber(), round.getOrderedByDoctorId(), round.getOrderedAt(), items)), true);
    }

    private static ClinicalOrderingService.RoundView sorted(ClinicalOrderingService.RoundView round) {
        return new ClinicalOrderingService.RoundView(round.id(), round.visitId(), round.roundNumber(), round.orderedByDoctorId(),
                round.orderedAt(), round.orders().stream().sorted(Comparator.comparing(o -> o.orderId().toString())).toList());
    }

    private static void validate(SubmitRequest command) {
        if (command == null || command.visitId() == null || !command.patientConsentConfirmed()
                || command.idempotencyKey() == null || command.idempotencyKey().isBlank()
                || command.idempotencyKey().length() > 128 || command.orders() == null || command.orders().isEmpty()
                || command.orders().size() > 200) throw new ClinicalOperationException("MSG-39");
        if (command.doctorProfileId() == null) throw new ClinicalOperationException("MSG-40");
        for (var order : command.orders()) {
            if (order == null || order.serviceId() == null || order.clinicalReason() == null
                    || order.clinicalReason().isBlank()) throw new ClinicalOperationException("MSG-39");
        }
    }

    private static String fingerprint(SubmitRequest command) {
        try {
            var bytes = new ByteArrayOutputStream();
            var out = new DataOutputStream(bytes);
            field(out, "clinical-laboratory-request-v1");
            field(out, command.doctorProfileId().toString());
            field(out, command.visitId().toString());
            out.writeInt(command.orders().size());
            for (var item : command.orders()) {
                field(out, item.serviceId().toString());
                field(out, item.clinicalReason());
                field(out, item.instructions());
            }
            out.flush();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Required request digest is unavailable", impossible);
        }
    }

    private static void field(DataOutputStream out, String value) throws IOException {
        if (value == null) { out.writeInt(-1); return; }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }
}
