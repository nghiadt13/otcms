package fu.fa26.sep490.g58.otcms_be.staff;

import fu.fa26.sep490.g58.otcms_be.staff.controller.DoctorProfileController;
import fu.fa26.sep490.g58.otcms_be.staff.exception.*;
import fu.fa26.sep490.g58.otcms_be.staff.mapper.DoctorProfileMapper;
import fu.fa26.sep490.g58.otcms_be.staff.model.*;
import fu.fa26.sep490.g58.otcms_be.staff.service.*;
import fu.fa26.sep490.g58.otcms_be.auth.security.JwtTokenService;
import fu.fa26.sep490.g58.otcms_be.auth.service.AccountAccessService;
import fu.fa26.sep490.g58.otcms_be.common.web.ProductionReadinessGateInterceptor;
import fu.fa26.sep490.g58.otcms_be.appointment.config.AppointmentWebConfiguration;
import fu.fa26.sep490.g58.otcms_be.appointment.web.PublicRequestRateLimitInterceptor;
import fu.fa26.sep490.g58.otcms_be.config.*;
import java.time.LocalDate;
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
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DoctorProfileController.class)
@Import({SecurityConfig.class, DoctorProfileMapper.class, DoctorProfileExceptionHandler.class, DoctorPublicProfileControllerTest.Fixture.class})
class DoctorPublicProfileControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean DoctorDirectoryService directory;
    @MockitoBean DoctorScheduleService schedules;
    @MockitoBean ProvisionDoctorProfileService provision;
    @MockitoBean ManageDoctorResourcesService resources;
    @MockitoBean JwtTokenService tokens;
    @MockitoBean AccountAccessService access;
    @MockitoBean AppointmentWebConfiguration appointmentWebConfiguration;
    @MockitoBean PublicRequestRateLimitInterceptor publicRequestRateLimitInterceptor;
    @MockitoBean ProductionReadinessGateInterceptor productionReadinessGateInterceptor;
    final UUID id = UUID.randomUUID();
    DoctorProfile profile() { return new DoctorProfile(id, UUID.randomUUID(), "Doctor", null, "private", "Orthopedics",
            "Qualification", "Biography", "Consultation", true, true, true); }
    @Test void anonymousDetailHasOnlyPublicFieldsAndSevenDates() throws Exception {
        LocalDate today = LocalDate.of(2026, 11, 2);
        when(directory.findPublicDetail(id)).thenReturn(Optional.of(new DoctorPublicDetail(profile(), today.datesUntil(today.plusDays(7))
                .map(date -> new DoctorPublicDetail.AvailabilityDay(date, date.equals(today))).toList())));
        String body = mvc.perform(get("/api/v1/doctors/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.availability.length()").value(7))
                .andExpect(jsonPath("$.availability[0].date").value("2026-11-02"))
                .andExpect(jsonPath("$.availability[0].hasOpenTime").value(true))
                .andExpect(jsonPath("$.availability[6].date").value("2026-11-08"))
                .andReturn().getResponse().getContentAsString();
        var json = tools.jackson.databind.json.JsonMapper.builder().build().readTree(body);
        Set<String> fields = new HashSet<>(); json.propertyNames().forEach(fields::add);
        assertEquals(Set.of("doctorProfileId", "fullName", "photoUrl", "specialties", "qualifications", "biography",
                "consultationInformation", "availability"), fields);
        Set<String> dayFields = new HashSet<>(); json.get("availability").get(0).propertyNames().forEach(dayFields::add);
        assertEquals(Set.of("date", "hasOpenTime"), dayFields);
    }
    @Test void publicListContractRemainsUnchangedAndDoesNotRequestDetails() throws Exception {
        when(directory.listPublic()).thenReturn(List.of(profile()));
        String body = mvc.perform(get("/api/v1/doctors")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].availability").doesNotExist()).andReturn().getResponse().getContentAsString();
        var first = tools.jackson.databind.json.JsonMapper.builder().build().readTree(body).get("items").get(0);
        Set<String> fields = new HashSet<>(); first.propertyNames().forEach(fields::add);
        assertEquals(Set.of("doctorProfileId", "fullName", "photoUrl", "specialties", "qualifications", "biography", "consultationInformation"), fields);
        verify(directory).listPublic(); verifyNoMoreInteractions(directory);
    }
    @Test void missingProfileKeepsBare404AndDependencyFailureUsesExisting503() throws Exception {
        when(directory.findPublicDetail(id)).thenReturn(Optional.empty());
        mvc.perform(get("/api/v1/doctors/{id}", id)).andExpect(status().isNotFound());
        var failure = new DoctorProfileException("DEPENDENCY_UNAVAILABLE"); failure.initCause(new IllegalStateException("private SQL detail"));
        doThrow(failure).when(directory).findPublicDetail(id);
        mvc.perform(get("/api/v1/doctors/{id}", id)).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("DEPENDENCY_UNAVAILABLE"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private SQL"))));
    }
    @TestConfiguration @EnableWebSecurity @EnableConfigurationProperties(SessionSecurityProperties.class)
    static class Fixture { }
}
