package fu.fa26.sep490.g58.otcms_be.clinical;

import static org.junit.jupiter.api.Assertions.*;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.ClinicVisitJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.VisitCareAssignmentJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.repository.ClinicVisitJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticRequestJpaRepository;
import java.util.Arrays;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

/** Parses owner JPQL against real Hibernate entity metadata. No SQL/database execution is claimed. */
class PastMedicalRecordsQuerySemanticTest {
    @Test void ownerBatchQueriesAreValidHibernateQueriesWithoutOpeningDatabaseConnection() {
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
                .applySetting("hibernate.hbm2ddl.auto", "none").build();
        try {
            var metadata = new MetadataSources(registry);
            for (var type : new Class<?>[] {ClinicVisitJpaEntity.class, VisitCareAssignmentJpaEntity.class,
                    DiagnosticRequestJpaEntity.class, DiagnosticFindingsVersionJpaEntity.class, DiagnosticReportJpaEntity.class}) {
                metadata.addAnnotatedClass(type);
            }
            try (var factory = metadata.buildMetadata().buildSessionFactory(); var session = factory.openSession()) {
                for (var repository : new Class<?>[] {ClinicVisitJpaRepository.class, DiagnosticRequestJpaRepository.class}) {
                    var method = Arrays.stream(repository.getDeclaredMethods())
                            .filter(m -> m.getName().equals("findHistoricalVisits") || m.getName().equals("findReleasedPatientHistory")).findFirst().orElseThrow();
                    assertDoesNotThrow(() -> session.createQuery(method.getAnnotation(Query.class).value(), Object.class));
                }
            }
        } finally { StandardServiceRegistryBuilder.destroy(registry); }
    }
}
