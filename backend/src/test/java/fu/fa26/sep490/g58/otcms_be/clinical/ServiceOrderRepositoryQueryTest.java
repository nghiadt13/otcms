package fu.fa26.sep490.g58.otcms_be.clinical;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fu.fa26.sep490.g58.otcms_be.clinical.repository.ServiceOrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

class ServiceOrderRepositoryQueryTest {
    @Test
    void completedLaboratoryHistoryUsesProductTableNames() throws NoSuchMethodException {
        Query query = ServiceOrderRepository.class
                .getMethod("findCompletedLaboratoryAssignments")
                .getAnnotation(Query.class);

        assertTrue(query.nativeQuery());
        assertTrue(query.value().contains("from service_orders o join service_order_rounds r"));
        assertFalse(query.value().contains("clinical_service_orders"));
        assertFalse(query.value().contains("clinical_ordering_rounds"));
    }
}
