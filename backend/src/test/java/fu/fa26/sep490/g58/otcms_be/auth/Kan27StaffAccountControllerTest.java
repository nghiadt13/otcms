package fu.fa26.sep490.g58.otcms_be.auth;

import fu.fa26.sep490.g58.otcms_be.auth.controller.*;
import fu.fa26.sep490.g58.otcms_be.auth.exception.*;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.StaffAccountDtos;
import fu.fa26.sep490.g58.otcms_be.auth.service.*;
import fu.fa26.sep490.g58.otcms_be.common.security.*;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class Kan27StaffAccountControllerTest {
    private final StaffAccountAdminService service = mock(StaffAccountAdminService.class);
    private final PatientAccountAdminService patients = mock(PatientAccountAdminService.class);
    private final UUID id = UUID.randomUUID();
    private final String correlation = UUID.randomUUID().toString();
    private final AuthenticatedAccount actor = new AuthenticatedAccount(UUID.randomUUID(), AccountRole.ADMIN, UUID.randomUUID());
    private MockMvc mvc;

    @BeforeEach void setup() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor, null, List.of()));
        mvc = MockMvcBuilders.standaloneSetup(new StaffAccountAdminController(service), new PatientAccountAdminController(patients))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new StaffAccountAdminApiExceptionHandler(), new PatientAccountAdminExceptionHandler())
                .addFilters(new CorrelationIdFilter()).build();
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    @Test void createReturnsExactSafeContractAndRequestCorrelation() throws Exception {
        when(service.create(eq(actor), eq("ORTHOPEDIC_DOCTOR"), eq("Doctor@Clinic"), eq("Abc12345"), eq(correlation)))
                .thenReturn(new StaffAccountDtos.Created(id, "Doctor@Clinic", "ORTHOPEDIC_DOCTOR", "ACTIVE", true));
        mvc.perform(post("/api/v1/admin/staff-accounts").header("X-Correlation-ID", correlation)
                        .contentType("application/json").content("""
                        {"role":"ORTHOPEDIC_DOCTOR","username":"Doctor@Clinic","initialPassword":"Abc12345"}
                        """))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.accountId").value(id.toString()))
                .andExpect(jsonPath("$.username").value("Doctor@Clinic"))
                .andExpect(jsonPath("$.role").value("ORTHOPEDIC_DOCTOR"))
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andExpect(jsonPath("$.initialPassword").doesNotExist())
                .andExpect(jsonPath("$.temporaryPassword").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test void listAndDetailAreNoStoreSafeAndUseDefaults() throws Exception {
        var item = new StaffAccountDtos.Item(id, null, "ORTHOPEDIC_DOCTOR", "INACTIVE", true, Instant.now(), null);
        when(service.list(actor, null, null, null, 0, 20)).thenReturn(new StaffAccountDtos.Page(List.of(item), 0, 20, 1, 1));
        when(service.detail(actor, id)).thenReturn(item);
        mvc.perform(get("/api/v1/admin/staff-accounts")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.items[0].role").value("ORTHOPEDIC_DOCTOR"))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].passwordHash").doesNotExist());
        String json = mvc.perform(get("/api/v1/admin/staff-accounts/" + id)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andReturn().getResponse().getContentAsString();
        for (String forbidden : List.of("passwordHash", "lockedUntil", "failedCredentialCount", "normalizedEmail",
                "workContact", "biography", "session", "token")) assertFalse(json.contains(forbidden));
    }

    @Test void mutationsPassExactCorrelationAndReissueIsNoStore() throws Exception {
        when(service.reissuePassword(actor, id, correlation)).thenReturn(new StaffAccountDtos.Reissued(id, "Ephemeral123", true));
        mvc.perform(post("/api/v1/admin/staff-accounts/" + id + "/activation").header("X-Correlation-ID", correlation))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/staff-accounts/" + id + "/deactivation").header("X-Correlation-ID", correlation))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/staff-accounts/" + id + "/password-reissues").header("X-Correlation-ID", correlation))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.temporaryPassword").value("Ephemeral123"))
                .andExpect(jsonPath("$.mustChangePassword").value(true));
        verify(service).activate(actor, id, correlation);
        verify(service).deactivate(actor, id, correlation);
    }

    @Test void patientStatusPassesRequestCorrelationWithoutDirectoryChanges() throws Exception {
        mvc.perform(post("/api/v1/admin/accounts/" + id + "/activation").header("X-Correlation-ID", correlation))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/accounts/" + id + "/deactivation").header("X-Correlation-ID", correlation))
                .andExpect(status().isOk());
        verify(patients).activate(actor, id, correlation);
        verify(patients).deactivate(actor, id, correlation);
    }

    @ParameterizedTest @CsvSource({"CREATE_INPUT,400,VALIDATION_FAILED", "SEARCH_INPUT,400,VALIDATION_FAILED",
            "USERNAME_TAKEN,409,USERNAME_TAKEN", "ACTOR_DENIED,403,ACCESS_DENIED",
            "TARGET_UNAVAILABLE,404,ACCESS_DENIED", "STATUS_UNCHANGED,409,ACCOUNT_STATUS_UNCHANGED",
            "OPERATION_FAILED,500,ACCOUNT_OPERATION_FAILED"})
    void errorContractIsScopedAndCorrelated(StaffAccountAdminException.Reason reason, int status, String code) throws Exception {
        when(service.detail(actor, id)).thenThrow(new StaffAccountAdminException(reason));
        mvc.perform(get("/api/v1/admin/staff-accounts/" + id).header("X-Correlation-ID", correlation))
                .andExpect(status().is(status)).andExpect(jsonPath("$.error.code").value(code))
                .andExpect(jsonPath("$.error.correlationId").value(correlation))
                .andExpect(jsonPath("$.error.messageCode").doesNotExist());
    }

    @Test void malformedCreateAndSearchHaveDifferentValidationMessages() throws Exception {
        mvc.perform(post("/api/v1/admin/staff-accounts").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.message").value("Thông tin không hợp lệ. Vui lòng kiểm tra và nhập lại."));
        mvc.perform(get("/api/v1/admin/staff-accounts").param("page", "NaN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("Tiêu chí tìm kiếm không hợp lệ. Vui lòng kiểm tra lại."));
        verifyNoInteractions(service);
    }

    @Test void malformedDetailUuidUsesNormalValidationMessage() throws Exception {
        mvc.perform(get("/api/v1/admin/staff-accounts/not-a-uuid").header("X-Correlation-ID", correlation))
                .andExpect(status().isBadRequest()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.message").value("Thông tin không hợp lệ. Vui lòng kiểm tra và nhập lại."))
                .andExpect(jsonPath("$.error.correlationId").value(correlation));
        verifyNoInteractions(service);
    }

    @Test void unexpectedRuntimeFailureReturnsSafeOperationError() throws Exception {
        String internalDetail = "Internal diagnostic must not escape";
        when(service.detail(actor, id)).thenThrow(new RuntimeException(internalDetail));
        String response = mvc.perform(get("/api/v1/admin/staff-accounts/" + id).header("X-Correlation-ID", correlation))
                .andExpect(status().isInternalServerError()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_OPERATION_FAILED"))
                .andExpect(jsonPath("$.error.message").value("Không thể hoàn tất yêu cầu. Hãy tải lại trạng thái trước khi thử lại."))
                .andExpect(jsonPath("$.error.correlationId").value(correlation))
                .andExpect(jsonPath("$.error.details").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertFalse(response.contains(internalDetail));
        assertFalse(response.contains("RuntimeException"));
        assertFalse(response.contains("stackTrace"));
    }
}
