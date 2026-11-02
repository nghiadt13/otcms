package fu.fa26.sep490.g58.otcms_be.auth.service;

import fu.fa26.sep490.g58.otcms_be.auth.model.dto.response.CredentialLoginResponse;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;

public interface CredentialLoginService {
    CredentialLoginResponse login(String email, String password, AccountType portal, String ip);

    CredentialLoginResponse loginStaff(String email, String password, String ip);

    CredentialLoginResponse loginAdministrator(String username, String password, String ip);
}
