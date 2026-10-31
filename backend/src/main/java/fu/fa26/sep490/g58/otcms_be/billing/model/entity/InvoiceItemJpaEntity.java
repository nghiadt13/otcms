package fu.fa26.sep490.g58.otcms_be.billing.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "invoice_items")
@IdClass(InvoiceItemJpaEntity.Key.class)
public class InvoiceItemJpaEntity {
    @Id
    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;
    @Id
    @Column(name = "line_number", nullable = false)
    private int lineNumber;
    @Column(name = "service_id")
    private UUID serviceId;
    @Column(name = "description_snapshot", nullable = false, length = 240)
    private String descriptionSnapshot;
    @Column(nullable = false)
    private int quantity;
    @Column(name = "unit_price_snapshot", nullable = false, precision = 14, scale = 2)
    private BigDecimal unitPriceSnapshot;

    protected InvoiceItemJpaEntity() { }

    public InvoiceItemJpaEntity(UUID invoiceId, int lineNumber, UUID serviceId, String description,
            int quantity, BigDecimal unitPrice) {
        this.invoiceId = invoiceId;
        this.lineNumber = lineNumber;
        this.serviceId = serviceId;
        this.descriptionSnapshot = description;
        this.quantity = quantity;
        this.unitPriceSnapshot = unitPrice;
    }

    public UUID getInvoiceId() { return invoiceId; }
    public int getLineNumber() { return lineNumber; }
    public UUID getServiceId() { return serviceId; }
    public String getDescriptionSnapshot() { return descriptionSnapshot; }
    public int getQuantity() { return quantity; }
    public BigDecimal getUnitPriceSnapshot() { return unitPriceSnapshot; }

    public static class Key implements Serializable {
        private UUID invoiceId;
        private int lineNumber;
        public Key() { }
        @Override public boolean equals(Object other) {
            return other instanceof Key key && lineNumber == key.lineNumber && Objects.equals(invoiceId, key.invoiceId);
        }
        @Override public int hashCode() { return Objects.hash(invoiceId, lineNumber); }
    }
}
