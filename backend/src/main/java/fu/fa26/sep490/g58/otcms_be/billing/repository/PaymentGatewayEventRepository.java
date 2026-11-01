package fu.fa26.sep490.g58.otcms_be.billing.repository;

import fu.fa26.sep490.g58.otcms_be.billing.model.entity.PaymentGatewayEventJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentGatewayEventRepository extends JpaRepository<PaymentGatewayEventJpaEntity, UUID> {
    boolean existsByProviderAndEventFingerprint(String provider, String eventFingerprint);
}
