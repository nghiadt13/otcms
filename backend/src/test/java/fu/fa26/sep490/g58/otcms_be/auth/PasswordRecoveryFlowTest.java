package fu.fa26.sep490.g58.otcms_be.auth;

import fu.fa26.sep490.g58.otcms_be.auth.controller.PasswordRecoveryController;
import fu.fa26.sep490.g58.otcms_be.auth.exception.AuthExceptionHandler;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.AccountJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.PasswordResetChallengeJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountStatus;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AccountRepository;
import fu.fa26.sep490.g58.otcms_be.auth.repository.PasswordResetChallengeRepository;
import fu.fa26.sep490.g58.otcms_be.auth.security.StaffNetworkPolicy;
import fu.fa26.sep490.g58.otcms_be.auth.service.AuthSessionService;
import fu.fa26.sep490.g58.otcms_be.auth.service.EmailDeliveryService;
import fu.fa26.sep490.g58.otcms_be.auth.service.impl.PasswordRecoveryServiceImpl;
import fu.fa26.sep490.g58.otcms_be.config.AuthProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PasswordRecoveryFlowTest {
    @Test void requestResetAndReplayUseTheSameChallengeAndUpdateTheActualPasswordHash() throws Exception {
        var accounts = mock(AccountRepository.class);
        var challenges = mock(PasswordResetChallengeRepository.class);
        var email = mock(EmailDeliveryService.class);
        var sessions = mock(AuthSessionService.class);
        var encoder = new BCryptPasswordEncoder(4);
        var account = new AccountJpaEntity();
        account.setId(UUID.randomUUID());
        account.setNormalizedEmail("patient@example.test");
        account.setAccountType(AccountType.PATIENT);
        account.setStatus(AccountStatus.ACTIVE);
        account.setPasswordHash(encoder.encode("old-password-123"));
        when(accounts.findByNormalizedEmail(account.getNormalizedEmail())).thenReturn(Optional.of(account));
        when(accounts.findByIdForUpdate(account.getId())).thenReturn(Optional.of(account));
        var service = new PasswordRecoveryServiceImpl(accounts, challenges, email, encoder, sessions,
                new AuthProperties(null, null, null, null, null, null, "v1", "v1"));
        var mvc = MockMvcBuilders.standaloneSetup(new PasswordRecoveryController(service, new StaffNetworkPolicy()))
                .setControllerAdvice(new AuthExceptionHandler()).build();
        mvc.perform(post("/api/auth/patient/forgot-password").contentType("application/json")
                .content("{\"email\":\"patient@example.test\"}"))
                .andExpect(status().isAccepted());
        var token = ArgumentCaptor.forClass(String.class);
        verify(email).sendPasswordReset(eq(account.getNormalizedEmail()), token.capture(), eq(account.getId()));
        var saved = ArgumentCaptor.forClass(PasswordResetChallengeJpaEntity.class);
        verify(challenges).saveAndFlush(saved.capture());
        var challenge = saved.getValue();
        assertNotEquals(token.getValue(), challenge.getTokenHash());
        when(challenges.findAccountIdByTokenHash(challenge.getTokenHash())).thenReturn(Optional.of(account.getId()));
        when(challenges.findByTokenHashForUpdate(challenge.getTokenHash())).thenReturn(Optional.of(challenge));
        String request = "{\"token\":\"" + token.getValue() + "\",\"newPassword\":\"new-password-123\"}";
        mvc.perform(post("/api/auth/patient/reset-password").contentType("application/json").content(request))
                .andExpect(status().isNoContent());
        assertTrue(encoder.matches("new-password-123", account.getPasswordHash()));
        assertFalse(encoder.matches("old-password-123", account.getPasswordHash()));
        verify(sessions).revokeAll(account.getId());
        mvc.perform(post("/api/auth/patient/reset-password").contentType("application/json").content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PASSWORD_RESET_TOKEN_INVALID"));
    }
}
