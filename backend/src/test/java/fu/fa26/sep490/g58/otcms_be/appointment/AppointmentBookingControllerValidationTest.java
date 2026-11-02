package fu.fa26.sep490.g58.otcms_be.appointment;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fu.fa26.sep490.g58.otcms_be.appointment.controller.AppointmentBookingController;
import fu.fa26.sep490.g58.otcms_be.appointment.exception.AppointmentBookingExceptionHandler;
import fu.fa26.sep490.g58.otcms_be.appointment.service.CancelAppointmentService;
import fu.fa26.sep490.g58.otcms_be.appointment.service.CheckInAppointmentService;
import fu.fa26.sep490.g58.otcms_be.appointment.service.CreateAppointmentService;
import fu.fa26.sep490.g58.otcms_be.appointment.service.MarkAppointmentNoShowService;
import fu.fa26.sep490.g58.otcms_be.appointment.service.ReassignAppointmentService;
import fu.fa26.sep490.g58.otcms_be.appointment.service.RescheduleAppointmentService;
import fu.fa26.sep490.g58.otcms_be.appointment.service.UpdateAppointmentStatusService;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AppointmentBookingControllerValidationTest {
    private CreateAppointmentService create;
    private CheckInAppointmentService checkIn;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        create = mock(CreateAppointmentService.class);
        checkIn = mock(CheckInAppointmentService.class);
        var controller = new AppointmentBookingController(create, mock(RescheduleAppointmentService.class),
                mock(CancelAppointmentService.class), mock(ReassignAppointmentService.class), checkIn,
                mock(MarkAppointmentNoShowService.class), mock(UpdateAppointmentStatusService.class));
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new AppointmentBookingExceptionHandler())
                .addFilters(new CorrelationIdFilter()).build();
    }

    @Test
    void invalidAndUnknownBookingFieldsMapToExistingValidationError() throws Exception {
        mvc.perform(post("/api/v1/appointments").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        mvc.perform(post("/api/v1/appointments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceId\":\"11111111-1111-1111-1111-111111111111\",\"doctorId\":\"22222222-2222-2222-2222-222222222222\",\"startTime\":\"2026-10-21T10:00:00+07:00\",\"purpose\":\"x\",\"extra\":true}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        mvc.perform(post("/api/v1/appointments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceId\":4}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        verifyNoInteractions(create);
    }

    @Test
    void checkInRejectsUnknownFieldsAndRequiresPatientAttestation() throws Exception {
        mvc.perform(post("/api/v1/appointments/{id}/check-in", "11111111-1111-1111-1111-111111111111")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"extra\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        mvc.perform(post("/api/v1/appointments/{id}/check-in", "11111111-1111-1111-1111-111111111111")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        verifyNoInteractions(checkIn);
    }
}
