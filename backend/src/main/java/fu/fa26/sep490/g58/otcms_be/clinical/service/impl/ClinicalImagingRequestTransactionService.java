package fu.fa26.sep490.g58.otcms_be.clinical.service.impl;

import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.ClinicalOrderingRoundRepository;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.ServiceOrderRepository;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalImagingRequestService.*;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalOrderingService;
import fu.fa26.sep490.g58.otcms_be.clinical.service.VisitConclusionPort;
import fu.fa26.sep490.g58.otcms_be.idempotency.model.dto.IdempotencyReplay;
import fu.fa26.sep490.g58.otcms_be.idempotency.model.IdempotencyScope;
import fu.fa26.sep490.g58.otcms_be.idempotency.service.IdempotencyService;
import java.time.Clock;
import java.util.Comparator;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** One local transaction owns round + orders + replay. Never catch uniqueness failure in this bean. */
@Service
public class ClinicalImagingRequestTransactionService {
    private final ClinicalOrderPreparationService preparation;
    private final ClinicalOrderingTransactionService writer;
    private final ServiceOrderRepository orders;
    private final ClinicalOrderingRoundRepository rounds;
    private final Clock clock;
    private final fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService guard;
    private final VisitConclusionPort conclusions;
    private final IdempotencyService store;

    public ClinicalImagingRequestTransactionService(ClinicalOrderPreparationService preparation,
            ClinicalOrderingTransactionService writer, ServiceOrderRepository orders, ClinicalOrderingRoundRepository rounds,
            Clock clock, fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService guard,
            VisitConclusionPort conclusions, IdempotencyService store) {
        this.preparation = preparation;
        this.writer = writer;
        this.orders = orders;
        this.rounds = rounds;
        this.clock = clock;
        this.guard = guard;
        this.conclusions = conclusions;
        this.store = store;
    }

    @Transactional
    public SubmissionResult submit(ImagingRequestCommand command, String fingerprint) {
        guard.acquire();
        var existing = store.find(IdempotencyScope.ORDER_ROUND_SUBMIT, command.doctorProfileId(), command.idempotencyKey());
        if (existing.isPresent()) return replay(command, fingerprint, existing.get());
        var context = preparation.requireCurrentCare(command.visitId(), command.doctorProfileId());
        var requests = command.orders().stream().map(order -> new ClinicalOrderingService.OrderRequest(
                order.serviceId(), order.clinicalReason(), order.bodyArea(), order.instructions())).toList();
        var snapshots = preparation.prepare(requests, true);
        // writer uses REQUIRED, joining this transaction; it cannot commit before replay insertion.
        var round = writer.createRound(command.visitId(), context.patientId(), command.doctorProfileId(), snapshots,
                command.idempotencyKey(), fingerprint);
        // The doctor now waits for imaging: the queue hands over to the next patient. Replays do not repeat this.
        conclusions.markAwaitingConclusion(command.visitId(), clock.instant());
        return new SubmissionResult(sorted(round), false);
    }

    /** Called only outside the failed attempt transaction, after its rollback has completed. */
    @Transactional
    public Optional<SubmissionResult> recoverReplay(ImagingRequestCommand command, String fingerprint) {
        guard.acquire();
        return store.find(IdempotencyScope.ORDER_ROUND_SUBMIT, command.doctorProfileId(), command.idempotencyKey())
                .map(existing -> replay(command, fingerprint, existing));
    }

    private SubmissionResult replay(ImagingRequestCommand command, String fingerprint, IdempotencyReplay recorded) {
        if (!recorded.matches(fingerprint)) throw new ClinicalOperationException("IDEMPOTENCY_CONFLICT");
        preparation.requireCurrentCare(command.visitId(), command.doctorProfileId());
        var replay = rounds.findById(recorded.resourceId())
                .orElseThrow(() -> new ClinicalOperationException("DEPENDENCY_UNAVAILABLE"));
        if (!command.visitId().equals(replay.getVisitId())
                || !command.doctorProfileId().equals(replay.getOrderedByDoctorId())) {
            throw new ClinicalOperationException("DEPENDENCY_UNAVAILABLE");
        }
        var views = orders.findByOrderingRoundId(replay.getId()).stream().map(order -> new ClinicalOrderingService.OrderView(
                order.getId(), order.getOrderCode(), order.getVisitId(), order.getPatientId(), order.getOrderingRoundId(),
                order.getServiceId(), order.getServiceNameSnapshot(), order.getServiceType(), order.getRequestingDoctorId(),
                order.getClinicalReason(), order.getBodyArea(), order.getInstructions(), order.getPriceSnapshot(),
                order.getOrderStatus(), order.getPaymentStatus())).toList();
        var result = sorted(new ClinicalOrderingService.RoundView(replay.getId(), replay.getVisitId(),
                replay.getRoundNumber(), replay.getOrderedByDoctorId(), replay.getOrderedAt(), views));
        return new SubmissionResult(result, true);
    }

    private static ClinicalOrderingService.RoundView sorted(ClinicalOrderingService.RoundView round) {
        return new ClinicalOrderingService.RoundView(round.id(), round.visitId(), round.roundNumber(), round.orderedByDoctorId(),
                round.orderedAt(), round.orders().stream().sorted(Comparator.comparing(order -> order.orderId().toString())).toList());
    }
}
