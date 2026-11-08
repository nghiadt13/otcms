package fu.fa26.sep490.g58.otcms_be.billing;

import static org.junit.jupiter.api.Assertions.*;
import fu.fa26.sep490.g58.otcms_be.billing.model.entity.*;
import fu.fa26.sep490.g58.otcms_be.billing.repository.BillingReadRepository;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

/** Real Hibernate parsing; this does not claim PostgreSQL SQL execution. */
class BillingReadQuerySemanticTest {
    @Test void listAndCountQueriesUseValidBillingEntityAttributes() {
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
                .applySetting("hibernate.hbm2ddl.auto", "none").build();
        try {
            var metadata = new MetadataSources(registry);
            for (var type : new Class<?>[]{InvoiceJpaEntity.class, InvoiceItemJpaEntity.class, PaymentAttemptJpaEntity.class}) {
                metadata.addAnnotatedClass(type);
            }
            try (var factory = metadata.buildMetadata().buildSessionFactory(); var session = factory.openSession()) {
                for (var method : BillingReadRepository.class.getDeclaredMethods()) {
                    var query = method.getAnnotation(Query.class);
                    if (query != null) {
                        assertDoesNotThrow(() -> session.createQuery(query.value(), Object.class));
                        assertDoesNotThrow(() -> session.createQuery(query.countQuery(), Long.class));
                    }
                }
            }
        } finally { StandardServiceRegistryBuilder.destroy(registry); }
    }
}
