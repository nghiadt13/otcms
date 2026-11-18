package fu.fa26.sep490.g58.otcms_be.visit;

import fu.fa26.sep490.g58.otcms_be.appointment.config.AppointmentWebConfiguration;
import fu.fa26.sep490.g58.otcms_be.appointment.web.PublicRequestRateLimitInterceptor;
import fu.fa26.sep490.g58.otcms_be.auth.security.JwtTokenService;
import fu.fa26.sep490.g58.otcms_be.auth.service.AccountAccessService;
import fu.fa26.sep490.g58.otcms_be.common.security.*;
import fu.fa26.sep490.g58.otcms_be.common.web.ProductionReadinessGateInterceptor;
import fu.fa26.sep490.g58.otcms_be.config.*;
import fu.fa26.sep490.g58.otcms_be.visit.controller.ServiceWorklistController;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitApiExceptionHandler;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.model.dto.response.ServiceWorklistDtos;
import fu.fa26.sep490.g58.otcms_be.visit.service.ServiceWorklistQueryService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** UC-46 worklist endpoint: roles, payload and MSG mapping. */
@WebMvcTest(ServiceWorklistController.class)
@Import({SecurityConfig.class, VisitApiExceptionHandler.class, ServiceWorklistControllerTest.Fixture.class})
class ServiceWorklistControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ServiceWorklistQueryService worklist;
    @MockitoBean JwtTokenService tokens;
    @MockitoBean AccountAccessService access;
    @MockitoBean AppointmentWebConfiguration appointmentWebConfiguration;
    @MockitoBean PublicRequestRateLimitInterceptor publicRequestRateLimitInterceptor;
    @MockitoBean ProductionReadinessGateInterceptor productionReadinessGateInterceptor;
    final UUID room = UUID.randomUUID();

    RequestPostProcessor actor(AccountRole role) {
        return authentication(new UsernamePasswordAuthenticationToken(new AuthenticatedAccount(UUID.randomUUID(), role, null),
                null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    @Test void technicianReadsTheRoomWorklistWithOrdersAndPaymentFlag() throws Exception {
        var order = new ServiceWorklistDtos.Order(UUID.randomUUID(), "CT-1", UUID.randomUUID(), "X-quang gối", "IMAGING",
                "ORDERED", false, "Đau", "Gối", "Tháo trang sức", "PENDING");
        var patient = new ServiceWorklistDtos.Patient(UUID.randomUUID(), "Nguyễn Văn A", LocalDate.of(1980, 1, 1), 46, "MALE");
        when(worklist.worklist(eq(room), any())).thenReturn(new ServiceWorklistDtos.Worklist(room, List.of(
                new ServiceWorklistDtos.Entry(UUID.randomUUID(), UUID.randomUUID(), 3, "WAITING", 1L, null, null, patient, List.of(order)))));
        mvc.perform(get("/api/v1/queue/rooms/{id}/service-worklist", room).with(actor(AccountRole.IMAGING_TECHNICIAN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].queueNumber").value(3))
                .andExpect(jsonPath("$.entries[0].patient.fullName").value("Nguyễn Văn A"))
                .andExpect(jsonPath("$.entries[0].orders[0].serviceName").value("X-quang gối"))
                .andExpect(jsonPath("$.entries[0].orders[0].instructions").value("Tháo trang sức"))
                .andExpect(jsonPath("$.entries[0].orders[0].paid").value(false));
    }

    @Test void otherRolesAndAnonymousAreDenied() throws Exception {
        mvc.perform(get("/api/v1/queue/rooms/{id}/service-worklist", room)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/queue/rooms/{id}/service-worklist", room).with(actor(AccountRole.PATIENT))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/queue/rooms/{id}/service-worklist", room).with(actor(AccountRole.DOCTOR))).andExpect(status().isForbidden());
        verifyNoInteractions(worklist);
    }

    @Test void roomOutsideTheTechniciansAssignmentIsMsg40AndUnknownRoomMsg41() throws Exception {
        when(worklist.worklist(eq(room), any())).thenThrow(new VisitOperationException("MSG-40"));
        mvc.perform(get("/api/v1/queue/rooms/{id}/service-worklist", room).with(actor(AccountRole.IMAGING_TECHNICIAN)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("MSG-40"));
        UUID missing = UUID.randomUUID();
        when(worklist.worklist(eq(missing), any())).thenThrow(new VisitOperationException("MSG-41"));
        mvc.perform(get("/api/v1/queue/rooms/{id}/service-worklist", missing).with(actor(AccountRole.RECEPTIONIST)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("MSG-41"));
    }

    @TestConfiguration @EnableWebSecurity @EnableConfigurationProperties(SessionSecurityProperties.class)
    static class Fixture { }
}
