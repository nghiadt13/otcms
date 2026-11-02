package fu.fa26.sep490.g58.otcms_be.visit;

import fu.fa26.sep490.g58.otcms_be.visit.controller.StaffRoomQueryController;
import fu.fa26.sep490.g58.otcms_be.visit.exception.*;
import fu.fa26.sep490.g58.otcms_be.visit.model.VisitRoom;
import fu.fa26.sep490.g58.otcms_be.visit.service.ManageVisitRoomService;
import fu.fa26.sep490.g58.otcms_be.auth.security.JwtTokenService;
import fu.fa26.sep490.g58.otcms_be.auth.service.AccountAccessService;
import fu.fa26.sep490.g58.otcms_be.common.security.*;
import fu.fa26.sep490.g58.otcms_be.common.web.ProductionReadinessGateInterceptor;
import fu.fa26.sep490.g58.otcms_be.appointment.config.AppointmentWebConfiguration;
import fu.fa26.sep490.g58.otcms_be.appointment.web.PublicRequestRateLimitInterceptor;
import fu.fa26.sep490.g58.otcms_be.config.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StaffRoomQueryController.class)
@Import({SecurityConfig.class, VisitApiExceptionHandler.class, StaffRoomQueryControllerTest.Fixture.class})
class StaffRoomQueryControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ManageVisitRoomService rooms;
    @MockitoBean JwtTokenService tokens;
    @MockitoBean AccountAccessService access;
    @MockitoBean AppointmentWebConfiguration appointmentWebConfiguration;
    @MockitoBean PublicRequestRateLimitInterceptor publicRequestRateLimitInterceptor;
    @MockitoBean ProductionReadinessGateInterceptor productionReadinessGateInterceptor;
    static final String PATH = "/api/v1/staff/rooms";
    RequestPostProcessor actor(AccountRole role) {
        return authentication(new UsernamePasswordAuthenticationToken(new AuthenticatedAccount(UUID.randomUUID(), role, null),
                null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }
    @ParameterizedTest @EnumSource(value = AccountRole.class, names = "PATIENT", mode = EnumSource.Mode.EXCLUDE)
    void allSixStaffRolesCanReadEmptyRooms(AccountRole role) throws Exception {
        when(rooms.listAll()).thenReturn(List.of());
        mvc.perform(get(PATH).with(actor(role))).andExpect(status().isOk()).andExpect(content().json("{\"items\":[]}"));
        verify(rooms).listAll(); verifyNoMoreInteractions(rooms);
    }
    @Test void patientAndAnonymousAreDeniedWithoutReadingInventory() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(get(PATH).with(actor(AccountRole.PATIENT))).andExpect(status().isForbidden());
        verifyNoInteractions(rooms);
    }
    @Test void reusesExactDtoAndPreservesServiceOrderAndInactiveRows() throws Exception {
        Instant now = Instant.parse("2026-11-01T00:00:00Z");
        when(rooms.listAll()).thenReturn(List.of(new VisitRoom(UUID.randomUUID(), "A", "Active", List.of("EXAM_ROOM"), true, now, now),
                new VisitRoom(UUID.randomUUID(), "B", "Inactive", List.of("EXAM_ROOM"), false, now, now)));
        var body = mvc.perform(get(PATH).with(actor(AccountRole.RECEPTIONIST))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].roomCode").value("A"))
                .andExpect(jsonPath("$.items[1].roomCode").value("B"))
                .andExpect(jsonPath("$.items[1].active").value(false)).andReturn().getResponse().getContentAsString();
        var first = tools.jackson.databind.json.JsonMapper.builder().build().readTree(body).get("items").get(0);
        Set<String> names = new HashSet<>(); first.propertyNames().forEach(names::add);
        assertEquals(Set.of("id", "roomCode", "displayLabel", "type", "description", "capabilities",
                "supportedServiceIds", "active", "createdAt", "updatedAt"), names);
    }
    @Test void dependencyFailureUsesExistingVisitEnvelope() throws Exception {
        when(rooms.listAll()).thenThrow(new VisitOperationException("DEPENDENCY_UNAVAILABLE"));
        String correlation = UUID.randomUUID().toString();
        mvc.perform(get(PATH).with(actor(AccountRole.RECEPTIONIST))
                .requestAttr(fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter.ATTRIBUTE, correlation))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error.code").value("DEPENDENCY_UNAVAILABLE"))
                .andExpect(jsonPath("$.error.correlationId").value(correlation));
    }
    @TestConfiguration @EnableWebSecurity @EnableConfigurationProperties(SessionSecurityProperties.class)
    static class Fixture { }
}
