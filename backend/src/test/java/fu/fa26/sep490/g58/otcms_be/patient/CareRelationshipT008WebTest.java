package fu.fa26.sep490.g58.otcms_be.patient;

import fu.fa26.sep490.g58.otcms_be.patient.controller.CareRelationshipController;
import fu.fa26.sep490.g58.otcms_be.patient.service.ConfirmCareRelationshipService;
import fu.fa26.sep490.g58.otcms_be.patient.service.RequestCareRelationshipService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CareRelationshipT008WebTest {
    private final UUID relationshipId = UUID.randomUUID();
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new CareRelationshipController(mock(fu.fa26.sep490.g58.otcms_be.patient.service.DependentProfileService.class))).build();

    @Test
    void legacyRequestAndConfirmationRoutesRemainAbsent() throws Exception {
        mvc.perform(post("/api/v1/care-relationships")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/care-relationships/{id}/confirm", relationshipId)).andExpect(status().isNotFound());
    }

    @Test
    void r3ControllerIsRegisteredAsMvcEndpoint() {
        org.junit.jupiter.api.Assertions.assertTrue(CareRelationshipController.class.isAnnotationPresent(org.springframework.web.bind.annotation.RestController.class));
        assertFalse(CareRelationshipController.class.isAnnotationPresent(org.springframework.stereotype.Controller.class));
    }
}
