package fu.fa26.sep490.g58.otcms_be.billing.service.impl;

import fu.fa26.sep490.g58.otcms_be.billing.exception.BillingException;
import fu.fa26.sep490.g58.otcms_be.billing.model.InvoiceItemStatus;
import fu.fa26.sep490.g58.otcms_be.billing.model.PaymentAttemptStatus;
import fu.fa26.sep490.g58.otcms_be.billing.model.entity.InvoiceItemJpaEntity;
import fu.fa26.sep490.g58.otcms_be.billing.model.entity.PaymentAttemptJpaEntity;
import fu.fa26.sep490.g58.otcms_be.billing.repository.InvoiceItemRepository;
import fu.fa26.sep490.g58.otcms_be.billing.repository.PaymentAttemptRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Locked, fresh view of every item of ONE invoice and of the attempts those items point to.
 *
 * <p>Lock order everywhere in billing: invoice row, then these item rows (line order), then attempts. Build the
 * snapshot right after the invoice lock and BEFORE modifying any entity: building it refreshes the managed
 * item/attempt entities from the database (open-in-view keeps them in a shared persistence context, so a
 * transaction that waited for the lock must not decide on a copy loaded earlier). After that every decision is made
 * on this snapshot, never on a second read.
 */
final class InvoiceItemSnapshot {
    private final List<InvoiceItemJpaEntity> items;
    private final Map<UUID, PaymentAttemptJpaEntity> attemptsById;
    private final Instant now;

    private InvoiceItemSnapshot(List<InvoiceItemJpaEntity> items, Map<UUID, PaymentAttemptJpaEntity> attemptsById,
            Instant now) {
        this.items = items;
        this.attemptsById = attemptsById;
        this.now = now;
    }

    static InvoiceItemSnapshot lock(InvoiceItemRepository itemRepository, PaymentAttemptRepository attemptRepository,
            EntityManager entityManager, UUID invoiceId, Instant now) {
        List<InvoiceItemJpaEntity> locked = itemRepository.lockAllByInvoice(invoiceId);
        locked.forEach(item -> refresh(entityManager, item));
        Set<UUID> attemptIds = locked.stream().map(InvoiceItemJpaEntity::getPaymentAttemptId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, PaymentAttemptJpaEntity> attempts = new HashMap<>();
        if (!attemptIds.isEmpty()) {
            for (PaymentAttemptJpaEntity attempt : attemptRepository.findAllById(attemptIds)) {
                refresh(entityManager, attempt);
                attempts.put(attempt.getId(), attempt);
            }
        }
        return new InvoiceItemSnapshot(locked, attempts, now);
    }

    private static void refresh(EntityManager entityManager, Object entity) {
        if (entityManager.contains(entity)) entityManager.refresh(entity);
    }

    List<InvoiceItemJpaEntity> items() { return items; }

    PaymentAttemptJpaEntity attemptOf(InvoiceItemJpaEntity item) {
        return item.getPaymentAttemptId() == null ? null : attemptsById.get(item.getPaymentAttemptId());
    }

    InvoiceItemStatus status(InvoiceItemJpaEntity item) {
        return InvoiceItemStatus.resolve(item, attemptOf(item), now);
    }

    /** Items nobody holds, in line order. */
    List<InvoiceItemJpaEntity> payable() {
        return items.stream().filter(item -> status(item) == InvoiceItemStatus.PENDING).toList();
    }

    /**
     * The requested items, all of which must belong to this invoice and be payable (MSG-49 otherwise: empty list,
     * null or repeated id, unknown id, an item that is paid, void, refunded or held by an open attempt).
     */
    List<InvoiceItemJpaEntity> pick(List<UUID> ids) {
        if (ids == null || ids.isEmpty() || ids.stream().anyMatch(Objects::isNull)
                || new HashSet<>(ids).size() != ids.size()) throw new BillingException("MSG-49");
        Set<UUID> wanted = new HashSet<>(ids);
        List<InvoiceItemJpaEntity> picked = items.stream().filter(item -> wanted.contains(item.getId())).toList();
        if (picked.size() != wanted.size()
                || picked.stream().anyMatch(item -> status(item) != InvoiceItemStatus.PENDING)) {
            throw new BillingException("MSG-49");
        }
        return picked;
    }

    /** Items that currently point at {@code attemptId}. */
    List<InvoiceItemJpaEntity> linkedTo(UUID attemptId) {
        return items.stream().filter(item -> attemptId.equals(item.getPaymentAttemptId())).toList();
    }

    static BigDecimal total(Collection<InvoiceItemJpaEntity> selected) {
        return selected.stream().map(item -> item.getUnitPriceSnapshot().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Sum of the items whose attempt is CONFIRMED. Must equal the confirmed attempt amounts of the invoice. */
    BigDecimal paidItemsTotal() {
        return total(items.stream().filter(item -> {
            var attempt = attemptOf(item);
            return attempt != null && attempt.getStatus() == PaymentAttemptStatus.CONFIRMED;
        }).toList());
    }

    /** Every item that is not void is paid, and there is at least one such item. */
    boolean allPaid() {
        List<InvoiceItemJpaEntity> live = items.stream().filter(item -> item.getVoidedAt() == null).toList();
        return !live.isEmpty() && live.stream().allMatch(item -> {
            var attempt = attemptOf(item);
            return attempt != null && attempt.getStatus() == PaymentAttemptStatus.CONFIRMED;
        });
    }

    /**
     * Points {@code picked} at {@code attempt}. Attempts that those items pointed at and that are still PENDING
     * (they can only be PENDING here when past their expiry) are closed as EXPIRED, so that no stale open attempt
     * keeps the invoice's single-active-online-attempt slot.
     */
    void claim(List<InvoiceItemJpaEntity> picked, PaymentAttemptJpaEntity attempt) {
        for (InvoiceItemJpaEntity item : picked) {
            var previous = attemptOf(item);
            if (previous != null && previous.getStatus() == PaymentAttemptStatus.PENDING
                    && !previous.getId().equals(attempt.getId())) {
                previous.fail(PaymentAttemptStatus.EXPIRED, "Items were taken by a newer payment");
            }
            item.setPaymentAttemptId(attempt.getId());
        }
        attemptsById.put(attempt.getId(), attempt);
    }
}
