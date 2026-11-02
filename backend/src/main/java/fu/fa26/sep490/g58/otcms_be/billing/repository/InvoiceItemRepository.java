package fu.fa26.sep490.g58.otcms_be.billing.repository;

import fu.fa26.sep490.g58.otcms_be.billing.model.entity.InvoiceItemJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceItemRepository extends JpaRepository<InvoiceItemJpaEntity, InvoiceItemJpaEntity.Key> {
    List<InvoiceItemJpaEntity> findAllByInvoiceIdOrderByLineNumber(UUID invoiceId);
}
