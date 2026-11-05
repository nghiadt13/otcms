package fu.fa26.sep490.g58.otcms_be.auth;

import fu.fa26.sep490.g58.otcms_be.auth.controller.MyAccountController;
import fu.fa26.sep490.g58.otcms_be.auth.exception.*;
import fu.fa26.sep490.g58.otcms_be.auth.service.*;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class Kan29MyAccountControllerTest {
    final MyAccountService accounts=mock(MyAccountService.class);
    final EmailChangeService emails=mock(EmailChangeService.class);
    MockMvc mvc;
    @BeforeEach void setup() {
        mvc=MockMvcBuilders.standaloneSetup(new MyAccountController(accounts,emails))
                .setControllerAdvice(new MyAccountApiExceptionHandler()).addFilters(new CorrelationIdFilter()).build();
    }
    @Test void unexpectedFailureNeverExposesDetailsAndKeepsCorrelation() throws Exception {
        when(accounts.get(any())).thenThrow(new IllegalStateException("internal SQL password token"));
        String correlation=java.util.UUID.randomUUID().toString();
        mvc.perform(get("/api/v1/me").header("X-Correlation-ID",correlation)).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("MSG-04"))
                .andExpect(jsonPath("$.error.correlationId").value(correlation))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("internal SQL"))));
    }
    @Test void oldOtpFieldAndNonTextCodeAreRejected() throws Exception {
        for(String body:new String[]{"{\"otp\":\"123456\"}","{\"code\":123456}"})
            mvc.perform(post("/api/v1/me/email-change/verify").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("MSG-08"));
        verifyNoInteractions(emails);
    }
    @Test void uploadPartMustBePhoto() throws Exception {
        mvc.perform(multipart("/api/v1/me/avatar").file(new MockMultipartFile("file","x","image/png",new byte[8]))
                .with(r -> {r.setMethod("PUT");return r;})).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MSG-08"));
        verifyNoInteractions(accounts);
    }
    @Test void scopedOtpErrorUsesTicketCode() throws Exception {
        doThrow(new MyAccountOperationException(MyAccountOperationException.Reason.OTP_INVALID)).when(emails).verify(any(),eq("123456"));
        mvc.perform(post("/api/v1/me/email-change/verify").contentType("application/json").content("{\"code\":\"123456\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("MSG-82"));
    }
}
