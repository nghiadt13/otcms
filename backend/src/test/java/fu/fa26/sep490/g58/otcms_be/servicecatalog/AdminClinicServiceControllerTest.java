package fu.fa26.sep490.g58.otcms_be.servicecatalog;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import fu.fa26.sep490.g58.otcms_be.appointment.config.AppointmentWebConfiguration;
import fu.fa26.sep490.g58.otcms_be.appointment.web.PublicRequestRateLimitInterceptor;
import fu.fa26.sep490.g58.otcms_be.auth.security.JwtTokenService;
import fu.fa26.sep490.g58.otcms_be.auth.service.AccountAccessService;
import fu.fa26.sep490.g58.otcms_be.common.web.ProductionReadinessGateInterceptor;
import fu.fa26.sep490.g58.otcms_be.config.SecurityConfig;
import fu.fa26.sep490.g58.otcms_be.config.SessionSecurityProperties;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.controller.ClinicServiceController;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.mapper.ClinicServiceMapper;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.dto.response.*;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ClinicServiceController.class)
@Import({SecurityConfig.class, AdminClinicServiceControllerTest.Fixture.class})
class AdminClinicServiceControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private GetClinicServiceCatalogService catalog;
    @MockitoBean private CreateClinicServiceService create;
    @MockitoBean private UpdateClinicServiceService update;
    @MockitoBean private ActivateClinicServiceService activate;
    @MockitoBean private DeactivateClinicServiceService deactivate;
    @MockitoBean private ManageDoctorServiceAssignmentsService assignments;
    @MockitoBean private ClinicServiceMapper mapper;
    @MockitoBean private JwtTokenService tokens;
    @MockitoBean private AccountAccessService access;
    @MockitoBean private AppointmentWebConfiguration appointmentWebConfiguration;
    @MockitoBean private PublicRequestRateLimitInterceptor publicRequestRateLimitInterceptor;
    @MockitoBean private ProductionReadinessGateInterceptor productionReadinessGateInterceptor;
    private final UUID id = UUID.randomUUID();
    private final String base = "/api/v1/admin/clinic-services";

    @Test void internalCatalogAndAssignmentsAreAdminOnly() throws Exception {
        for (String path : List.of(base, base + "/" + id + "/doctor-assignments")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            for (String role : List.of("OWNER", "DOCTOR", "PATIENT", "RECEPTIONIST"))
                mvc.perform(get(path).with(user("other").roles(role))).andExpect(status().isForbidden());
        }
        verifyNoInteractions(catalog, assignments);
    }

    @Test void adminCanReadInactiveServiceAndAssignedDoctorIds() throws Exception {
        when(catalog.adminCatalog()).thenReturn(List.of());
        when(mapper.toItemsResponse(anyList())).thenReturn(new ItemsResponse(List.of(new ServiceResponse(id,
                "Internal service", null, java.math.BigDecimal.TEN, 20, 5, false, false))));
        mvc.perform(get(base).with(user("admin").roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].active").value(false));
        UUID doctor = UUID.randomUUID();
        when(assignments.list(id)).thenReturn(List.of(new DoctorAssignmentResponse(UUID.randomUUID(), id, doctor)));
        mvc.perform(get(base + "/" + id + "/doctor-assignments").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].doctorProfileId").value(doctor.toString()));
    }

    @TestConfiguration @EnableWebSecurity @EnableConfigurationProperties(SessionSecurityProperties.class)
    static class Fixture { }
}
