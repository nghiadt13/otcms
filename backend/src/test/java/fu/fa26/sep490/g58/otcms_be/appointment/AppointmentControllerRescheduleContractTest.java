package fu.fa26.sep490.g58.otcms_be.appointment;

import fu.fa26.sep490.g58.otcms_be.appointment.controller.AppointmentAvailabilityController;
import fu.fa26.sep490.g58.otcms_be.appointment.controller.AppointmentBookingController;
import fu.fa26.sep490.g58.otcms_be.appointment.controller.AppointmentQueryController;
import fu.fa26.sep490.g58.otcms_be.appointment.exception.AppointmentAvailabilityException;
import fu.fa26.sep490.g58.otcms_be.appointment.exception.AppointmentAvailabilityExceptionHandler;
import fu.fa26.sep490.g58.otcms_be.appointment.exception.AppointmentBookingException;
import fu.fa26.sep490.g58.otcms_be.appointment.exception.AppointmentBookingExceptionHandler;
import fu.fa26.sep490.g58.otcms_be.appointment.model.CreatedAppointment;
import fu.fa26.sep490.g58.otcms_be.appointment.service.AppointmentAvailabilityQuery;
import fu.fa26.sep490.g58.otcms_be.appointment.service.AppointmentAvailabilitySlot;
import fu.fa26.sep490.g58.otcms_be.appointment.service.AppointmentRescheduleCountQueryService;
import fu.fa26.sep490.g58.otcms_be.appointment.service.AppointmentSearchPage;
import fu.fa26.sep490.g58.otcms_be.appointment.service.AppointmentSummary;
import fu.fa26.sep490.g58.otcms_be.appointment.service.CancelAppointmentService;
import fu.fa26.sep490.g58.otcms_be.appointment.service.CheckInAppointmentService;
import fu.fa26.sep490.g58.otcms_be.appointment.service.CreateAppointmentService;
import fu.fa26.sep490.g58.otcms_be.appointment.service.GetAppointmentAvailabilityService;
import fu.fa26.sep490.g58.otcms_be.appointment.service.GetAppointmentChangeAvailabilityService;
import fu.fa26.sep490.g58.otcms_be.appointment.service.GetAppointmentsService;
import fu.fa26.sep490.g58.otcms_be.appointment.service.MarkAppointmentNoShowService;
import fu.fa26.sep490.g58.otcms_be.appointment.service.ReassignAppointmentService;
import fu.fa26.sep490.g58.otcms_be.appointment.service.RescheduleAppointmentCommand;
import fu.fa26.sep490.g58.otcms_be.appointment.service.RescheduleAppointmentService;
import fu.fa26.sep490.g58.otcms_be.appointment.service.UpdateAppointmentStatusService;
import fu.fa26.sep490.g58.otcms_be.billing.service.AppointmentInvoicePaidStatusQueryService;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import fu.fa26.sep490.g58.otcms_be.config.AppointmentBookingProperties;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorProfileIdentityQueryService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AppointmentControllerRescheduleContractTest {
    private static final Instant NOW = Instant.parse("2026-10-14T00:00:00Z");

    @Test
    void rescheduleAndAvailabilityEndpointsAllowPatientRoleInPreAuthorize() throws NoSuchMethodException {
        var rescheduleMethod = AppointmentBookingController.class.getMethod("reschedule", UUID.class,
                fu.fa26.sep490.g58.otcms_be.appointment.model.dto.request.RescheduleAppointmentRequest.class,
                AuthenticatedAccount.class, HttpServletRequest.class);
        var rescheduleAuth = rescheduleMethod.getAnnotation(PreAuthorize.class).value();
        assertTrue(rescheduleAuth.contains("ROLE_PATIENT"));
        assertTrue(rescheduleAuth.contains("ROLE_RECEPTIONIST"));
        assertTrue(rescheduleAuth.contains("ROLE_ADMIN"));

        var availMethod = AppointmentAvailabilityController.class.getMethod("rescheduleAvailability",
                String.class, String.class, String.class);
        var availAuth = availMethod.getAnnotation(PreAuthorize.class).value();
        assertTrue(availAuth.contains("ROLE_PATIENT"));
        assertTrue(availAuth.contains("ROLE_RECEPTIONIST"));
        assertTrue(availAuth.contains("ROLE_ADMIN"));
    }

    @Test
    void queryControllerPopulatesExamFeePaidAndPatientRescheduleCounts() throws Exception {
        UUID accountId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID doctorId = UUID.randomUUID();

        var getAppointments = mock(GetAppointmentsService.class);
        var billingStatus = mock(AppointmentInvoicePaidStatusQueryService.class);
        var rescheduleCounts = mock(AppointmentRescheduleCountQueryService.class);
        var properties = new AppointmentBookingProperties(30, 120, 30, 15, 15,
                ZoneId.of("Asia/Ho_Chi_Minh"), new AppointmentBookingProperties.PublicRateLimit(60, 60), 15, 30000L, false);

        var summary = new AppointmentSummary(appointmentId, patientId, serviceId, doctorId, "CONFIRMED",
                "PATIENT_ONLINE", "Checkup", NOW.plusSeconds(86400), NOW.plusSeconds(88200),
                30, 5, NOW.plusSeconds(88500), null, null, NOW, NOW, "Dr A", "Consult",
                BigDecimal.valueOf(200000), "Patient A", null);
        when(getAppointments.detail(accountId, AccountRole.PATIENT, appointmentId)).thenReturn(summary);
        when(getAppointments.list(eq(accountId), eq(AccountRole.PATIENT), any(), eq(0), eq(20)))
                .thenReturn(new AppointmentSearchPage(List.of(summary), 0, 20, 1, 1));
        when(billingStatus.paidStatus(List.of(appointmentId))).thenReturn(Map.of(appointmentId, true));
        when(rescheduleCounts.countPatientReschedules(appointmentId)).thenReturn(1);
        when(rescheduleCounts.countPatientReschedules(List.of(appointmentId))).thenReturn(Map.of(appointmentId, 1));

        var principal = new AuthenticatedAccount(accountId, AccountRole.PATIENT, UUID.randomUUID());
        var controller = new AppointmentQueryController(getAppointments, properties, billingStatus, rescheduleCounts,
                Clock.fixed(NOW, ZoneId.of("UTC")));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(principalResolver(principal))
                .addFilters(new CorrelationIdFilter()).build();

        mvc.perform(get("/api/v1/appointments/{id}", appointmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.examFeePaid").value(true))
                .andExpect(jsonPath("$.patientRescheduleCount").value(1))
                .andExpect(jsonPath("$.remainingReschedules").value(1))
                .andExpect(jsonPath("$.maxPatientReschedules").value(2));

        mvc.perform(get("/api/v1/appointments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].examFeePaid").value(true))
                .andExpect(jsonPath("$.items[0].patientRescheduleCount").value(1))
                .andExpect(jsonPath("$.items[0].remainingReschedules").value(1))
                .andExpect(jsonPath("$.items[0].maxPatientReschedules").value(2));
    }

    @Test
    void bookingAndAvailabilityControllersReturnEnrichedRescheduleResponseAndExpectedErrorCodes() throws Exception {
        UUID accountId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID doctorId = UUID.randomUUID();
        Instant target = NOW.plusSeconds(172800);

        var rescheduleService = mock(RescheduleAppointmentService.class);
        var cancelService = mock(CancelAppointmentService.class);
        var billingStatus = mock(AppointmentInvoicePaidStatusQueryService.class);
        var rescheduleCounts = mock(AppointmentRescheduleCountQueryService.class);
        var changeAvailability = mock(GetAppointmentChangeAvailabilityService.class);

        var principal = new AuthenticatedAccount(accountId, AccountRole.PATIENT, UUID.randomUUID());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_PATIENT"))));
        try {
            var bookingController = new AppointmentBookingController(mock(CreateAppointmentService.class),
                    rescheduleService, cancelService, mock(ReassignAppointmentService.class),
                    mock(CheckInAppointmentService.class), mock(MarkAppointmentNoShowService.class),
                    mock(UpdateAppointmentStatusService.class), billingStatus, rescheduleCounts);
            var availabilityController = new AppointmentAvailabilityController(
                    mock(GetAppointmentAvailabilityService.class), changeAvailability,
                    mock(DoctorProfileIdentityQueryService.class));

            MockMvc mvc = MockMvcBuilders.standaloneSetup(bookingController, availabilityController)
                    .setControllerAdvice(new AppointmentBookingExceptionHandler(),
                            new AppointmentAvailabilityExceptionHandler())
                    .setCustomArgumentResolvers(principalResolver(principal))
                    .addFilters(new CorrelationIdFilter()).build();

            var updated = new CreatedAppointment(appointmentId, patientId, serviceId, doctorId, "CONFIRMED",
                    "PATIENT_ONLINE", "Checkup", target, target.plusSeconds(1800), 30, 5, target.plusSeconds(2100),
                    null, null, NOW, NOW);
            when(rescheduleService.reschedule(eq(appointmentId), any(RescheduleAppointmentCommand.class)))
                    .thenReturn(updated);
            when(billingStatus.paidStatus(List.of(appointmentId))).thenReturn(Map.of(appointmentId, true));
            when(rescheduleCounts.countPatientReschedules(appointmentId)).thenReturn(1);

            mvc.perform(patch("/api/v1/appointments/{id}/reschedule", appointmentId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"startTime\":\"" + OffsetDateTime.ofInstant(target, ZoneId.of("UTC")) + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.examFeePaid").value(true))
                    .andExpect(jsonPath("$.patientRescheduleCount").value(1))
                    .andExpect(jsonPath("$.remainingReschedules").value(1))
                    .andExpect(jsonPath("$.maxPatientReschedules").value(2));

            when(cancelService.cancel(eq(appointmentId), any()))
                    .thenThrow(new AppointmentBookingException("APPOINTMENT_NOT_CHANGEABLE"));
            mvc.perform(post("/api/v1/appointments/{id}/cancel", appointmentId)
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("MSG-32"));

            when(changeAvailability.findRescheduleSlots(appointmentId, accountId,
                    AppointmentAvailabilityQuery.Audience.PUBLIC, LocalDate.of(2026, 10, 16), LocalDate.of(2026, 10, 16)))
                    .thenReturn(List.of(new AppointmentAvailabilitySlot(serviceId, doctorId, "Dr A",
                            target, target.plusSeconds(1800), target.plusSeconds(2100))));
            mvc.perform(get("/api/v1/appointments/{id}/reschedule-availability", appointmentId)
                            .param("from", "2026-10-16").param("to", "2026-10-16"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.slots[0].doctorId").value(doctorId.toString()));

            when(changeAvailability.findRescheduleSlots(appointmentId, accountId,
                    AppointmentAvailabilityQuery.Audience.PUBLIC, LocalDate.of(2026, 10, 16), LocalDate.of(2026, 10, 16)))
                    .thenThrow(new AppointmentAvailabilityException("APPOINTMENT_CHANGE_TOO_LATE"));
            mvc.perform(get("/api/v1/appointments/{id}/reschedule-availability", appointmentId)
                            .param("from", "2026-10-16").param("to", "2026-10-16"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("MSG-32"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static HandlerMethodArgumentResolver principalResolver(AuthenticatedAccount principal) {
        return new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return AuthenticatedAccount.class.isAssignableFrom(parameter.getParameterType());
            }

            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                    NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                return principal;
            }
        };
    }
}
