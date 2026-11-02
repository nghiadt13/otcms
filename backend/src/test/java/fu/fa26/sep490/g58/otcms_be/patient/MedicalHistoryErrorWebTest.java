package fu.fa26.sep490.g58.otcms_be.patient;

import fu.fa26.sep490.g58.otcms_be.common.security.*;
import fu.fa26.sep490.g58.otcms_be.patient.controller.PatientMedicalHistoryController;
import fu.fa26.sep490.g58.otcms_be.patient.exception.MedicalHistoryExceptionHandler;
import fu.fa26.sep490.g58.otcms_be.patient.service.PatientMedicalHistoryService;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MedicalHistoryErrorWebTest {
    @Test void unexpectedAccountLookupFailureCannotLeakInternals() throws Exception {
        var service = mock(PatientMedicalHistoryService.class);
        UUID actor = UUID.randomUUID(), profile = UUID.randomUUID();
        when(service.get(any(), eq(profile))).thenThrow(new IllegalStateException("private account details"));
        var mvc = MockMvcBuilders.standaloneSetup(new PatientMedicalHistoryController(service))
                .setControllerAdvice(new MedicalHistoryExceptionHandler())
                .setCustomArgumentResolvers(new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver()).build();
        try {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    new AuthenticatedAccount(actor, AccountRole.PATIENT, UUID.randomUUID()), null, List.of()));
            mvc.perform(get("/api/v1/patient-profiles/{id}/medical-history", profile))
                    .andExpect(status().isInternalServerError()).andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.error.code").value("MSG-04"))
                    .andExpect(jsonPath("$.error.message").value("Không thể hoàn tất yêu cầu. Vui lòng thử lại sau hoặc bắt đầu lại."))
                    .andExpect(content().string(not(containsString("private account details"))));
        } finally { SecurityContextHolder.clearContext(); }
    }
}
