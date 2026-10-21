package fu.fa26.sep490.g58.otcms_be.auth.service;

import fu.fa26.sep490.g58.otcms_be.auth.model.dto.request.ChangePasswordRequest;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.request.ResetPasswordRequest;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;

import java.util.UUID;

public interface PasswordRecoveryService {
    String GENERIC_MESSAGE = "If the account is eligible, password reset instructions will be sent.";

    String forgot(String identifier, AccountType portal);

    void reset(ResetPasswordRequest request, AccountType portal);

    void change(UUID accountId, ChangePasswordRequest request);
}
