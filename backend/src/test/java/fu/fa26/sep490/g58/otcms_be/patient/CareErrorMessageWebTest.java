package fu.fa26.sep490.g58.otcms_be.patient;

import fu.fa26.sep490.g58.otcms_be.common.security.*;
import fu.fa26.sep490.g58.otcms_be.patient.controller.CareRelationshipController;
import fu.fa26.sep490.g58.otcms_be.patient.exception.*;
import fu.fa26.sep490.g58.otcms_be.patient.service.DependentProfileService;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** HTTP advice assertions; persistence concurrency is exercised by the PostgreSQL suites. */
class CareErrorMessageWebTest {
    final DependentProfileService service = mock(DependentProfileService.class);
    final UUID actor = UUID.randomUUID(), profile = UUID.randomUUID();
    final MockMvc mvc = MockMvcBuilders.standaloneSetup(new CareRelationshipController(service))
            .setControllerAdvice(new CareRelationshipExceptionHandler())
            .setCustomArgumentResolvers(new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver())
            .build();

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "VALIDATION_FAILED|400|Thông tin hồ sơ chưa hợp lệ. Vui lòng kiểm tra và nhập lại.",
        "CARE_LINK_NOT_FOUND|404|Không thể quản lý hồ sơ người phụ thuộc này. Vui lòng kiểm tra lại mối quan hệ chăm sóc.",
        "OTP_INVALID|400|Mã xác thực không hợp lệ hoặc đã hết hạn. Vui lòng kiểm tra và thử lại.",
        "ACCESS_DENIED|403|Không có quyền thực hiện thao tác này."
    })
    void applicationMessagesRemainVietnamese(String code, int status, String message) throws Exception {
        when(service.detail(actor, profile)).thenThrow(new CareRelationshipException(code));
        try {
            authenticate();
            mvc.perform(get("/api/v1/me/dependents/{id}", profile))
                    .andExpect(status().is(status)).andExpect(jsonPath("$.error.message").value(message))
                    .andExpect(jsonPath("$.error.message", not(containsString("?"))));
        } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    }
    @Test void dependentPutMapsPersistenceConflictTo409WithoutClientVersion() throws Exception {
        when(service.update(eq(actor), eq(profile), any())).thenThrow(new ObjectOptimisticLockingFailureException(Object.class, profile));
        try {
            authenticate();
            mvc.perform(put("/api/v1/me/dependents/{id}", profile).contentType("application/json")
                    .content("{\"fullName\":\"Nguyễn An\",\"dateOfBirth\":\"2000-01-02\",\"gender\":\"OTHER\",\"phone\":\"0912345678\"}"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("STALE_VERSION"))
                    .andExpect(jsonPath("$.error.message").value("Không thể hoàn tất yêu cầu. Hãy tải lại trạng thái trước khi thử lại."))
                    .andExpect(jsonPath("$.error.message", not(containsString("?"))));
        } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    }
    @Test void unexpectedFailureUsesIntactGenericMessage() throws Exception {
        when(service.detail(actor, profile)).thenThrow(new IllegalStateException("private internal detail"));
        try {
            authenticate();
            mvc.perform(get("/api/v1/me/dependents/{id}", profile)).andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.error.code").value("MSG-04"))
                    .andExpect(jsonPath("$.error.message").value("Không thể hoàn tất yêu cầu. Vui lòng thử lại sau hoặc bắt đầu lại."))
                    .andExpect(jsonPath("$.error.message", not(containsString("?"))));
        } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    }
    void authenticate() {
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new AuthenticatedAccount(actor, AccountRole.PATIENT, UUID.randomUUID()), null, List.of()));
    }
}
