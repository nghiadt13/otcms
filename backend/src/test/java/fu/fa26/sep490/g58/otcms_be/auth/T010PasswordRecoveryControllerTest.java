package fu.fa26.sep490.g58.otcms_be.auth;

import fu.fa26.sep490.g58.otcms_be.auth.controller.PasswordRecoveryController;
import fu.fa26.sep490.g58.otcms_be.auth.security.StaffNetworkPolicy;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.request.ForgotPasswordRequest;
import fu.fa26.sep490.g58.otcms_be.auth.service.PasswordRecoveryService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class T010PasswordRecoveryControllerTest {
    @Test void patientForgotReturnsGenericAcceptedResponse() throws Exception {
        PasswordRecoveryService service = mock(PasswordRecoveryService.class);
        when(service.forgot("patient@example.test", fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType.PATIENT))
                .thenReturn(PasswordRecoveryService.GENERIC_MESSAGE);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new PasswordRecoveryController(service, new StaffNetworkPolicy())).build();
        mvc.perform(post("/api/auth/patient/forgot-password").contentType("application/json")
                        .content("{\"email\":\"patient@example.test\"}"))
                .andExpect(status().isAccepted()).andExpect(content().string(PasswordRecoveryService.GENERIC_MESSAGE));
    }

    @Test void invalidForgotPayloadIsRejectedBeforeServiceCall() throws Exception {
        PasswordRecoveryService service = mock(PasswordRecoveryService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new PasswordRecoveryController(service, new StaffNetworkPolicy())).build();
        mvc.perform(post("/api/auth/patient/forgot-password").contentType("application/json").content("{\"email\":\"\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void staffForgotIsDeniedOutsideAllowedNetwork() throws Exception {
        PasswordRecoveryService service = mock(PasswordRecoveryService.class);
        StaffNetworkPolicy policy = new StaffNetworkPolicy(); policy.setAllowedCidrs(java.util.List.of("10.0.0.0/8"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new PasswordRecoveryController(service, policy)).setControllerAdvice(new fu.fa26.sep490.g58.otcms_be.auth.exception.AuthExceptionHandler()).build();
        mvc.perform(post("/api/auth/staff/forgot-password").with(r -> { r.setRemoteAddr("192.168.1.10"); return r; })
                        .contentType("application/json").content("{\"email\":\"staff@example.test\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test void malformedResetPayloadIsRejectedBeforeServiceCall() throws Exception {
        PasswordRecoveryService service = mock(PasswordRecoveryService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new PasswordRecoveryController(service, new StaffNetworkPolicy())).build();
        mvc.perform(post("/api/auth/patient/reset-password").contentType("application/json")
                        .content("{\"token\":\"\",\"newPassword\":\"\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
