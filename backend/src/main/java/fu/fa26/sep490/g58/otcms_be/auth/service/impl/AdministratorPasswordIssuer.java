package fu.fa26.sep490.g58.otcms_be.auth.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.AccountJpaEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Shared UC07/UC08 issuance; only the hash and mandatory-change flag enter persistence. */
@Component
public class AdministratorPasswordIssuer {
    private final TemporaryPasswordGenerator generator;
    private final PasswordEncoder encoder;

    public AdministratorPasswordIssuer(TemporaryPasswordGenerator generator, PasswordEncoder encoder) {
        this.generator = generator;
        this.encoder = encoder;
    }

    public String issue(AccountJpaEntity account) {
        String issued = generator.generate();
        account.setPasswordHash(encoder.encode(issued));
        account.setMustChangePassword(true);
        return issued;
    }
}
