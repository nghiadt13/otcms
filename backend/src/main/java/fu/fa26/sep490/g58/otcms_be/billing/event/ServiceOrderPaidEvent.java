package fu.fa26.sep490.g58.otcms_be.billing.event;

import java.util.UUID;

/**
 * Published (inside the payment transaction) when the invoice item of a clinical service order becomes PAID, by
 * counter cash or by a confirmed PayOS payment. Listeners that must act only after the commit use
 * {@code @TransactionalEventListener}. A paid item that was voided meanwhile does not publish it.
 */
public record ServiceOrderPaidEvent(UUID serviceOrderId, UUID invoiceItemId, UUID invoiceId) { }
