package fu.fa26.sep490.g58.otcms_be.clinical.service.impl;

import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.model.entity.ClinicalImagingRequestReplayJpaEntity;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.ClinicalImagingRequestReplayRepository;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.OrderingRoundRepository;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.ServiceOrderRepository;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalImagingRequestService.*;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalOrderingService;
import fu.fa26.sep490.g58.otcms_be.clinical.service.VisitConclusionPort;
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
    private final ClinicalImagingRequestReplayRepository replays;
    private final OrderingRoundRepository rounds;
    private final ServiceOrderRepository orders;
    private final Clock clock;
    private final fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService guard;
    private final VisitConclusionPort conclusions;

    public ClinicalImagingRequestTransactionService(ClinicalOrderPreparationService preparation,
            ClinicalOrderingTransactionService writer, ClinicalImagingRequestReplayRepository replays,
            OrderingRoundRepository rounds, ServiceOrderRepository orders, Clock clock,
            fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService guard,
            VisitConclusionPort conclusions) {
        this.preparation = preparation;
        this.writer = writer;
        this.replays = replays;
        this.rounds = rounds;
        this.orders = orders;
        this.clock = clock;
        this.guard = guard;
        this.conclusions = conclusions;
    }

    @Transactional
    public SubmissionResult submit(ImagingRequestCommand command, String fingerprint) {
        guard.acquire();
        var existing = replays.findByDoctorProfileIdAndIdempotencyKey(command.doctorProfileId(), command.idempotencyKey());
        if (existing.isPresent()) return replay(command, fingerprint, existing.get());
        var context = preparation.requireCurrentCare(command.visitId(), command.doctorProfileId());
        var requests = command.orders().stream().map(order -> new ClinicalOrderingService.OrderRequest(
                order.serviceId(), order.clinicalReason(), order.bodyArea(), order.instructions())).toList();
        var snapshots = preparation.prepare(requests, true);
        // writer uses REQUIRED, joining this transaction; it cannot commit before replay insertion.
        var round = writer.createRound(command.visitId(), context.patientId(), command.doctorProfileId(), snapshots);
        // The doctor now waits for imaging: the queue hands over to the next patient. Replays do not repeat this.
        conclusions.markAwaitingConclusion(command.visitId(), clock.instant());
        replays.saveAndFlush(new ClinicalImagingRequestReplayJpaEntity(command.doctorProfileId(), command.idempotencyKey(),
                fingerprint, round.id(), clock.instant()));
        return new SubmissionResult(sorted(round), false);
    }

    /** Called only outside the failed attempt transaction, after its rollback has completed. */
    @Transactional
    public Optional<SubmissionResult> recoverReplay(ImagingRequestCommand command, String fingerprint) {
        guard.acquire();
        return replays.findByDoctorProfileIdAndIdempotencyKey(command.doctorProfileId(), command.idempotencyKey())
                .map(existing -> replay(command, fingerprint, existing));
    }

    private SubmissionResult replay(ImagingRequestCommand command, String fingerprint, ClinicalImagingRequestReplayJpaEntity replay) {
        if (!fingerprint.equals(replay.getRequestFingerprint())) throw new ClinicalOperationException("IDEMPOTENCY_CONFLICT");
        preparation.requireCurrentCare(command.visitId(), command.doctorProfileId());
        var round = rounds.findById(replay.getOrderingRoundId()).orElseThrow(() -> new ClinicalOperationException("DEPENDENCY_UNAVAILABLE"));
        if (!command.visitId().equals(round.getVisitId()) || !command.doctorProfileId().equals(round.getOrderedByDoctorId())) {
            throw new ClinicalOperationException("DEPENDENCY_UNAVAILABLE");
        }
        var views = orders.findByOrderingRoundId(round.getId()).stream().map(order -> new ClinicalOrderingService.OrderView(
                order.getId(), order.getOrderCode(), order.getVisitId(), order.getPatientId(), order.getOrderingRoundId(),
                order.getServiceId(), order.getServiceNameSnapshot(), order.getServiceType(), order.getRequestingDoctorId(),
                order.getClinicalReason(), order.getBodyArea(), order.getInstructions(), order.getPriceSnapshot(),
                order.getOrderStatus(), order.getPaymentStatus())).toList();
        var result = sorted(new ClinicalOrderingService.RoundView(round.getId(), round.getVisitId(),
                round.getRoundNumber(), round.getOrderedByDoctorId(), round.getOrderedAt(), views));
        return new SubmissionResult(result, true);
    }

    private static ClinicalOrderingService.RoundView sorted(ClinicalOrderingService.RoundView round) {
        return new ClinicalOrderingService.RoundView(round.id(), round.visitId(), round.roundNumber(), round.orderedByDoctorId(),
                round.orderedAt(), round.orders().stream().sorted(Comparator.comparing(order -> order.orderId().toString())).toList());
    }
}
