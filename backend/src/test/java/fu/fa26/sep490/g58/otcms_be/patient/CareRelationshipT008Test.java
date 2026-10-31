package fu.fa26.sep490.g58.otcms_be.patient;

import fu.fa26.sep490.g58.otcms_be.patient.controller.CareRelationshipController;
import fu.fa26.sep490.g58.otcms_be.patient.repository.CareRelationshipRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import fu.fa26.sep490.g58.otcms_be.patient.service.impl.ConfirmCareRelationshipServiceImpl;
import fu.fa26.sep490.g58.otcms_be.patient.service.impl.RequestCareRelationshipServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CareRelationshipT008Test {
    @Test
    void formerDependentAppointmentWorkflowIsNotRegisteredAsApplicationOrPersistenceBeans() {
        assertFalse(RequestCareRelationshipServiceImpl.class.isAnnotationPresent(Service.class));
        assertFalse(ConfirmCareRelationshipServiceImpl.class.isAnnotationPresent(Service.class));
        assertTrue(JpaRepository.class.isAssignableFrom(CareRelationshipRepository.class));
        assertFalse(RequestCareRelationshipServiceImpl.class.isAnnotationPresent(Component.class));
    }

    @Test
    void noDependentRelationshipHttpControllerIsRegistered() {
        assertFalse(CareRelationshipController.class.isAnnotationPresent(RestController.class));
    }
}
