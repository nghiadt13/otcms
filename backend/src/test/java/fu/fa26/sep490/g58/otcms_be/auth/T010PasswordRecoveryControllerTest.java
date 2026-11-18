package fu.fa26.sep490.g58.otcms_be.auth;

import fu.fa26.sep490.g58.otcms_be.auth.controller.PasswordRecoveryController;
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
    @Test void unicodeResetPasswordOver72BytesReturnsValidation400() throws Exception {
        var accounts = mock(fu.fa26.sep490.g58.otcms_be.auth.repository.AccountRepository.class);
        var challenges = mock(fu.fa26.sep490.g58.otcms_be.auth.repository.VerificationCodeRepository.class);
        var encoder = mock(org.springframework.security.crypto.password.PasswordEncoder.class);
        var sessions = mock(fu.fa26.sep490.g58.otcms_be.auth.service.AuthSessionService.class);
        var service = new fu.fa26.sep490.g58.otcms_be.auth.service.impl.PasswordRecoveryServiceImpl(
                accounts, challenges, mock(fu.fa26.sep490.g58.otcms_be.auth.service.EmailDeliveryService.class),
                encoder, sessions, mock(fu.fa26.sep490.g58.otcms_be.config.AuthProperties.class));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new PasswordRecoveryController(service))
                .setControllerAdvice(new fu.fa26.sep490.g58.otcms_be.auth.exception.AuthExceptionHandler()).build();
        // 25 code points, letters and digits present, 73 UTF-8 bytes: only the 72-byte rule rejects it.
        String newPassword = "ả".repeat(24) + "1";
        mvc.perform(post("/api/auth/patient/reset-password").contentType("application/json")
                        .content("{\"email\":\"patient@example.test\",\"otp\":\"123456\",\"newPassword\":\"" + newPassword + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MSG-08"));
        verifyNoInteractions(accounts, challenges, encoder, sessions);
    }

    @Test void patientForgotReturnsGenericAcceptedResponse() throws Exception {
        PasswordRecoveryService service = mock(PasswordRecoveryService.class);
        when(service.forgot("patient@example.test", fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType.PATIENT))
                .thenReturn(PasswordRecoveryService.GENERIC_MESSAGE);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new PasswordRecoveryController(service)).build();
        mvc.perform(post("/api/auth/patient/forgot-password").contentType("application/json")
                        .content("{\"email\":\"patient@example.test\"}"))
                .andExpect(status().isAccepted()).andExpect(content().string(PasswordRecoveryService.GENERIC_MESSAGE));
    }

    @Test void invalidForgotPayloadIsRejectedBeforeServiceCall() throws Exception {
        PasswordRecoveryService service = mock(PasswordRecoveryService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new PasswordRecoveryController(service)).build();
        mvc.perform(post("/api/auth/patient/forgot-password").contentType("application/json").content("{\"email\":\"\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void staffSelfServiceRecoveryEndpointsAreNotExposed() throws Exception {
        PasswordRecoveryService service = mock(PasswordRecoveryService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new PasswordRecoveryController(service)).build();
        mvc.perform(post("/api/auth/staff/forgot-password").contentType("application/json")
                        .content("{\"email\":\"staff@example.test\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/auth/staff/reset-password").contentType("application/json")
                        .content("{\"token\":\"token\",\"newPassword\":\"new-password-123\"}"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(service);
    }

    @Test void malformedResetPayloadIsRejectedBeforeServiceCall() throws Exception {
        PasswordRecoveryService service = mock(PasswordRecoveryService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new PasswordRecoveryController(service)).build();
        mvc.perform(post("/api/auth/patient/reset-password").contentType("application/json")
                        .content("{\"token\":\"\",\"newPassword\":\"\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
